package com.lms.repayment.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import com.lms.repayment.domain.Mandate;
import com.lms.repayment.domain.Repayment;
import com.lms.repayment.domain.RepaymentChannel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepaymentServicesTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-13T06:00:00Z"), ZoneOffset.UTC);
    private static final Money INR = Money.of(474_000L, Currency.getInstance("INR"));
    private static final UUID LOAN = UUID.randomUUID();

    private RepaymentFakes.FakeRepaymentRepository repayments;
    private RepaymentFakes.FakeMandateRepository mandates;
    private RepaymentFakes.FakeDedup dedup;
    private RepaymentFakes.RecordingPublisher publisher;
    private RecordRepaymentService recordService;
    private ReverseRepaymentService reverseService;
    private RegisterMandateService registerService;

    @BeforeEach
    void setUp() {
        repayments = new RepaymentFakes.FakeRepaymentRepository();
        mandates = new RepaymentFakes.FakeMandateRepository();
        dedup = new RepaymentFakes.FakeDedup();
        publisher = new RepaymentFakes.RecordingPublisher();
        recordService = new RecordRepaymentService(repayments, publisher, dedup, CLOCK);
        reverseService = new ReverseRepaymentService(repayments, publisher, dedup, CLOCK);
        registerService =
                new RegisterMandateService(
                        mandates, new RepaymentFakes.FakeMandateClient(), publisher, CLOCK, "cb");
    }

    @Test
    void recordPublishesReceivedOnceAndDedupsDuplicates() {
        Optional<Repayment> first =
                recordService.record(LOAN, INR, RepaymentChannel.MANDATE, null, null, "RAIL-1");
        Optional<Repayment> duplicate =
                recordService.record(LOAN, INR, RepaymentChannel.MANDATE, null, null, "RAIL-1");

        assertThat(first).isPresent();
        assertThat(duplicate).isEmpty(); // same (ref, RECEIVED) → one repayment only
        assertThat(publisher.events).containsExactly("repayment.received:RAIL-1");
    }

    @Test
    void reverseFindsTheReceiptAndPublishesReversed() {
        recordService.record(LOAN, INR, RepaymentChannel.MANDATE, null, null, "RAIL-1");

        Optional<Repayment> reversed = reverseService.reverse("RAIL-1", "DEBIT_RETURNED");

        assertThat(reversed).isPresent();
        assertThat(publisher.events).contains("repayment.reversed:RAIL-1:DEBIT_RETURNED");
    }

    @Test
    void reversingAnUnknownReferenceIsANoOp() {
        assertThat(reverseService.reverse("never", "x")).isEmpty();
        assertThat(publisher.events).noneMatch(e -> e.startsWith("repayment.reversed"));
    }

    @Test
    void registerMandatePersistsAndPublishes() {
        Mandate mandate = registerService.register(LOAN, "cust-1");
        assertThat(mandates.findActiveByLoanId(LOAN)).isPresent();
        assertThat(publisher.events).contains("mandate.registered:" + mandate.id());
    }
}
