package com.lms.ledger.application;

import com.lms.events.Uuid7;
import com.lms.ledger.application.port.out.JournalEntryRepository;
import com.lms.ledger.application.port.out.LedgerEventPublisher;
import com.lms.ledger.domain.JournalEntry;
import com.lms.ledger.domain.Posting;
import com.lms.ledger.domain.PostingInput;
import com.lms.ledger.domain.PostingTemplate;
import com.lms.ledger.domain.PostingTemplateRegistry;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Posts a balanced journal entry for a source event, selecting the template by event type and
 * enforcing idempotency on the source event id (docs/domain-rules.md "Ledger invariants" rule 3).
 * An event with no registered template is ignored (not every consumed event posts to the ledger).
 */
public class PostEntryService {

    private final JournalEntryRepository entries;
    private final PostingTemplateRegistry registry;
    private final LedgerEventPublisher publisher;
    private final Clock clock;

    public PostEntryService(
            JournalEntryRepository entries,
            PostingTemplateRegistry registry,
            LedgerEventPublisher publisher,
            Clock clock) {
        this.entries = Objects.requireNonNull(entries);
        this.registry = Objects.requireNonNull(registry);
        this.publisher = Objects.requireNonNull(publisher);
        this.clock = Objects.requireNonNull(clock);
    }

    public Optional<JournalEntry> post(PostingRequest request) {
        Objects.requireNonNull(request, "request");
        Optional<PostingTemplate> template = registry.find(request.eventType());
        if (template.isEmpty()) {
            return Optional.empty(); // event type does not post to the ledger (yet)
        }
        if (entries.existsForSourceEvent(request.sourceEventId())) {
            return Optional.empty(); // idempotent: already posted for this source event
        }
        List<Posting> postings =
                template.get()
                        .build(
                                new PostingInput(
                                        request.loanId(),
                                        request.customerId(),
                                        request.currency(),
                                        request.amounts()));
        JournalEntry entry =
                new JournalEntry(
                        Uuid7.generate(clock),
                        request.sourceEventId(),
                        request.eventType(),
                        request.currency(),
                        postings,
                        request.loanId(),
                        request.customerId(),
                        null,
                        clock.instant(),
                        request.externalReference(),
                        request.externalAmountMinor());
        entries.save(entry);
        publisher.entryPosted(entry);
        return Optional.of(entry);
    }
}
