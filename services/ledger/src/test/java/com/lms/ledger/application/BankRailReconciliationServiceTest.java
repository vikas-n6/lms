package com.lms.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.ledger.application.port.out.ExternalReconFeed;
import com.lms.ledger.application.port.out.JournalEntryRepository;
import com.lms.ledger.application.port.out.LedgerEventPublisher;
import com.lms.ledger.application.port.out.ReconRunRepository;
import com.lms.ledger.domain.JournalEntry;
import com.lms.ledger.domain.recon.ReconClassification;
import com.lms.ledger.domain.recon.ReconLine;
import com.lms.ledger.domain.recon.ReconResult;
import com.lms.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The bank/rail reconciliation service (phase prompt task 6): it matches the external feeds against
 * the ledger money-movement references and publishes a BANK_RAIL result. A clean day is all
 * MATCHED; injected drift is detected. The matcher itself is covered by {@code ReconMatcherTest}.
 */
class BankRailReconciliationServiceTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);

    private static ReconLine line(String ref, long minor) {
        return new ReconLine(ref, Money.of(minor, INR));
    }

    @Test
    void cleanDayPublishesAllMatched() {
        FakeFeed feed = new FakeFeed(List.of(line("UTR-1", 474_000)), List.of());
        FakeEntries entries = new FakeEntries(List.of(line("UTR-1", 474_000)));
        FakeRuns runs = new FakeRuns();
        FakePublisher publisher = new FakePublisher();
        var service =
                new BankRailReconciliationService(feed, entries, runs, publisher, "INR", CLOCK);

        UUID runId = service.reconcile(DATE);

        assertThat(runId).isNotNull();
        assertThat(publisher.lastResult.isClean()).isTrue();
        assertThat(runs.saved).hasSize(1);
        assertThat(publisher.lastResult.count(ReconClassification.MATCHED)).isEqualTo(1);
    }

    @Test
    void driftDayDetectsDiscrepancies() {
        FakeFeed feed =
                new FakeFeed(
                        List.of(line("UTR-1", 474_000), line("UTR-ONLY-BANK", 100_000)),
                        List.of(line("UTR-RAIL", 4_882_000)));
        FakeEntries entries =
                new FakeEntries(List.of(line("UTR-1", 474_000), line("UTR-ONLY-LEDGER", 50_000)));
        FakeRuns runs = new FakeRuns();
        FakePublisher publisher = new FakePublisher();
        var service =
                new BankRailReconciliationService(feed, entries, runs, publisher, "INR", CLOCK);

        service.reconcile(DATE);

        ReconResult r = publisher.lastResult;
        assertThat(r.isClean()).isFalse();
        assertThat(r.count(ReconClassification.MATCHED)).isEqualTo(1);
        assertThat(r.count(ReconClassification.MISSING_IN_LEDGER))
                .isEqualTo(2); // UTR-ONLY-BANK, UTR-RAIL
        assertThat(r.count(ReconClassification.MISSING_IN_BANK)).isEqualTo(1); // UTR-ONLY-LEDGER
    }

    private record FakeFeed(List<ReconLine> bank, List<ReconLine> rail)
            implements ExternalReconFeed {
        @Override
        public List<ReconLine> bankStatement(LocalDate date) {
            return bank;
        }

        @Override
        public List<ReconLine> railReport(LocalDate date) {
            return rail;
        }
    }

    private record FakeEntries(List<ReconLine> lines) implements JournalEntryRepository {
        @Override
        public List<ReconLine> reconLines(LocalDate date) {
            return lines;
        }

        @Override
        public boolean existsForSourceEvent(UUID sourceEventId) {
            return false;
        }

        @Override
        public boolean isReversed(UUID entryId) {
            return false;
        }

        @Override
        public void save(JournalEntry entry) {}

        @Override
        public Optional<JournalEntry> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public List<JournalEntry> find(
                Optional<String> loanId, Optional<LocalDate> date, Optional<String> accountCode) {
            return List.of();
        }

        @Override
        public List<JournalEntry> findPostedUpTo(LocalDate asOf) {
            return List.of();
        }

        @Override
        public List<JournalEntry> findAll() {
            return List.of();
        }
    }

    private static final class FakeRuns implements ReconRunRepository {
        final List<ReconResult> saved = new ArrayList<>();

        @Override
        public UUID saveRun(LocalDate reconDate, String currency, ReconResult result, Instant at) {
            saved.add(result);
            return UUID.randomUUID();
        }

        @Override
        public void resolveItem(UUID itemId, String note) {}

        @Override
        public List<Run> listRuns() {
            return List.of();
        }

        @Override
        public List<StoredItem> itemsForRun(UUID runId) {
            return List.of();
        }

        @Override
        public Optional<StoredItem> findItem(UUID itemId) {
            return Optional.empty();
        }
    }

    private static final class FakePublisher implements LedgerEventPublisher {
        ReconResult lastResult;

        @Override
        public void entryPosted(JournalEntry entry) {}

        @Override
        public void entryReversed(JournalEntry reversalEntry, String reason) {}

        @Override
        public void trialBalanceBroken(long diffMinorUnits, String currencyCode) {}

        @Override
        public void bankRailReconCompleted(UUID runId, String currencyCode, ReconResult result) {
            this.lastResult = result;
        }
    }
}
