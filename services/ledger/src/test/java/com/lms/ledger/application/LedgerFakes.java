package com.lms.ledger.application;

import com.lms.ledger.application.port.out.JournalEntryRepository;
import com.lms.ledger.application.port.out.LedgerEventPublisher;
import com.lms.ledger.domain.JournalEntry;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** In-memory fakes for ledger application tests. */
final class LedgerFakes {

    private LedgerFakes() {}

    static final class FakeJournalEntryRepository implements JournalEntryRepository {
        final List<JournalEntry> saved = new ArrayList<>();

        @Override
        public boolean existsForSourceEvent(UUID sourceEventId) {
            return saved.stream().anyMatch(e -> e.sourceEventId().equals(sourceEventId));
        }

        @Override
        public boolean isReversed(UUID entryId) {
            return saved.stream().anyMatch(e -> entryId.equals(e.reversesEntryId()));
        }

        @Override
        public void save(JournalEntry entry) {
            saved.add(entry);
        }

        @Override
        public Optional<JournalEntry> findById(UUID id) {
            return saved.stream().filter(e -> e.id().equals(id)).findFirst();
        }

        @Override
        public List<JournalEntry> find(
                Optional<String> loanId, Optional<LocalDate> date, Optional<String> accountCode) {
            return saved.stream()
                    .filter(e -> loanId.map(l -> l.equals(e.loanId())).orElse(true))
                    .filter(
                            e ->
                                    date.map(
                                                    d ->
                                                            e.occurredAt()
                                                                    .atZone(ZoneOffset.UTC)
                                                                    .toLocalDate()
                                                                    .equals(d))
                                            .orElse(true))
                    .filter(
                            e ->
                                    accountCode
                                            .map(
                                                    c ->
                                                            e.postings().stream()
                                                                    .anyMatch(
                                                                            p ->
                                                                                    p.account()
                                                                                            .code()
                                                                                            .equals(
                                                                                                    c)))
                                            .orElse(true))
                    .toList();
        }

        @Override
        public List<JournalEntry> findPostedUpTo(LocalDate asOf) {
            return saved.stream()
                    .filter(e -> !e.occurredAt().atZone(ZoneOffset.UTC).toLocalDate().isAfter(asOf))
                    .toList();
        }

        @Override
        public List<JournalEntry> findAll() {
            return List.copyOf(saved);
        }
    }

    static final class RecordingPublisher implements LedgerEventPublisher {
        final List<String> events = new ArrayList<>();
        long lastBrokenDiff;

        @Override
        public void entryPosted(JournalEntry entry) {
            events.add("ledger.entry-posted:" + entry.sourceEventType());
        }

        @Override
        public void entryReversed(JournalEntry reversalEntry, String reason) {
            events.add("ledger.entry-reversed:" + reversalEntry.reversesEntryId() + ":" + reason);
        }

        @Override
        public void trialBalanceBroken(long diffMinorUnits, String currencyCode) {
            this.lastBrokenDiff = diffMinorUnits;
            events.add("ledger.trial-balance-broken:" + diffMinorUnits);
        }
    }
}
