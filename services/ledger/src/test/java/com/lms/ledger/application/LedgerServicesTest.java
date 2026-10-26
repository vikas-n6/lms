package com.lms.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.ledger.domain.JournalEntry;
import com.lms.ledger.domain.PostingTemplateRegistry;
import com.lms.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LedgerServicesTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-14T06:00:00Z"), ZoneOffset.UTC);

    private LedgerFakes.FakeJournalEntryRepository repo;
    private LedgerFakes.RecordingPublisher publisher;
    private PostEntryService postService;
    private ReverseEntryService reverseService;

    private static Money inr(long m) {
        return Money.of(m, INR);
    }

    @BeforeEach
    void setUp() {
        repo = new LedgerFakes.FakeJournalEntryRepository();
        publisher = new LedgerFakes.RecordingPublisher();
        postService =
                new PostEntryService(repo, PostingTemplateRegistry.defaults(), publisher, CLOCK);
        reverseService = new ReverseEntryService(repo, publisher, CLOCK);
    }

    private PostingRequest disbursementSucceeded(UUID sourceEventId) {
        return new PostingRequest(
                "disbursement.succeeded",
                sourceEventId,
                "loan-1",
                "cust-1",
                INR,
                Map.of(
                        "principal", inr(5_000_000),
                        "net", inr(4_882_000),
                        "fee", inr(100_000),
                        "gst", inr(18_000)));
    }

    @Test
    void postsABalancedEntryForADisbursement() {
        var entry = postService.post(disbursementSucceeded(UUID.randomUUID()));

        assertThat(entry).isPresent();
        assertThat(entry.get().totalDebits()).isEqualTo(inr(5_000_000));
        assertThat(publisher.events).containsExactly("ledger.entry-posted:disbursement.succeeded");
    }

    @Test
    void postingIsIdempotentOnTheSourceEvent() {
        UUID sourceEventId = UUID.randomUUID();
        postService.post(disbursementSucceeded(sourceEventId));
        var second = postService.post(disbursementSucceeded(sourceEventId));

        assertThat(second).isEmpty();
        assertThat(repo.saved).hasSize(1);
    }

    @Test
    void anUnregisteredEventTypePostsNothing() {
        var result =
                postService.post(
                        new PostingRequest(
                                "loan.unknown-event", // no template is registered for this type
                                UUID.randomUUID(),
                                "loan-1",
                                "cust-1",
                                INR,
                                Map.of()));
        assertThat(result).isEmpty();
        assertThat(repo.saved).isEmpty();
    }

    @Test
    void reversingRestoresBalancesAndRejectsASecondReversal() {
        JournalEntry original =
                postService.post(disbursementSucceeded(UUID.randomUUID())).orElseThrow();

        JournalEntry reversal = reverseService.reverse(original.id(), "correction");
        assertThat(reversal.reversesEntryId()).isEqualTo(original.id());
        assertThat(publisher.events)
                .contains("ledger.entry-reversed:" + original.id() + ":correction");

        assertThatThrownBy(() -> reverseService.reverse(original.id(), "again"))
                .isInstanceOf(EntryAlreadyReversedException.class);
    }

    @Test
    void reversalRequiresAReasonAndAnExistingEntry() {
        JournalEntry original =
                postService.post(disbursementSucceeded(UUID.randomUUID())).orElseThrow();
        assertThatThrownBy(() -> reverseService.reverse(original.id(), "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reverseService.reverse(UUID.randomUUID(), "x"))
                .isInstanceOf(EntryNotFoundException.class);
    }

    @Test
    void reconciliationIsZeroForABalancedBook() {
        postService.post(disbursementSucceeded(UUID.randomUUID()));
        ReconciliationService recon = new ReconciliationService(repo, publisher, "INR");

        assertThat(recon.reconcile()).isZero();
        assertThat(publisher.events).noneMatch(e -> e.startsWith("ledger.trial-balance-broken"));
    }
}
