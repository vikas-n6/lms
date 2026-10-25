package com.lms.loanaccount.adapters.out.events;

import com.lms.contracts.events.MoneyAmount;
import com.lms.contracts.loan.LoanActivated;
import com.lms.contracts.loan.LoanCancelled;
import com.lms.contracts.loan.LoanClosed;
import com.lms.contracts.loan.LoanCreated;
import com.lms.contracts.loan.LoanForeclosed;
import com.lms.contracts.loan.LoanInterestAccrued;
import com.lms.contracts.loan.LoanRepaymentAllocated;
import com.lms.contracts.loan.LoanRepaymentReversed;
import com.lms.events.EventHeaders;
import com.lms.events.EventMeta;
import com.lms.events.EventMetadataMapper;
import com.lms.events.Uuid7;
import com.lms.loanaccount.application.port.out.LoanEventPublisher;
import com.lms.loanaccount.domain.AllocationResult;
import com.lms.loanaccount.domain.Loan;
import com.lms.money.Money;
import com.lms.outbox.OutboxRecord;
import com.lms.outbox.OutboxRepository;
import com.lms.security.context.RequestContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.stereotype.Component;

/**
 * Writes loan lifecycle events to the outbox in the same transaction as the loan change (never
 * straight to Kafka — docs/architecture.md). Keyed by loan id so events for one loan stay ordered.
 */
@Component
public class OutboxLoanEventPublisher implements LoanEventPublisher {

    private static final String TOPIC = "loan.events";
    private static final String AGGREGATE = "loan";
    private static final String PRODUCER = "loan-account";
    private static final int SCHEMA_VERSION = 1;

    private final OutboxRepository outbox;
    private final Serializer<Object> serializer;
    private final Clock clock;

    public OutboxLoanEventPublisher(
            OutboxRepository outbox, Serializer<Object> serializer, Clock clock) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void loanCreated(Loan loan, UUID causationEventId) {
        String loanId = loan.id().toString();
        EventMeta meta = meta("loan.created", loanId, causationEventId);
        LoanCreated event =
                LoanCreated.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setLoanId(loanId)
                        .setLoanNumber(loan.loanNumber())
                        .setApplicationId(loan.applicationId())
                        .setCustomerId(loan.customerId())
                        .setProductId(loan.productId())
                        .setProductVersion(loan.productVersion())
                        .setPrincipal(money(loan.principal()))
                        .setTenorMonths(loan.tenorMonths())
                        .setEmi(money(loan.emi()))
                        .setFee(money(loan.fee()))
                        .setGst(money(loan.gst()))
                        .setNetDisbursal(money(loan.netDisbursal()))
                        .setAnnualInterestRatePercent(loan.annualInterestRate().asPlainString())
                        .setCreatedAt(clock.instant())
                        .build();
        append(meta, loanId, event);
    }

    @Override
    public void loanActivated(
            Loan loan, LocalDate firstDueDate, LocalDate maturityDate, UUID causationEventId) {
        String loanId = loan.id().toString();
        EventMeta meta = meta("loan.activated", loanId, causationEventId);
        LoanActivated event =
                LoanActivated.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setLoanId(loanId)
                        .setLoanNumber(loan.loanNumber())
                        .setDisbursementDate(loan.disbursementDate())
                        .setFirstDueDate(firstDueDate)
                        .setMaturityDate(maturityDate)
                        .setActivatedAt(clock.instant())
                        .build();
        append(meta, loanId, event);
    }

    @Override
    public void interestAccrued(
            UUID loanId,
            String loanNumber,
            LocalDate businessDate,
            int instalmentNumber,
            Money amount) {
        String id = loanId.toString();
        EventMeta meta = meta("loan.interest-accrued", id, null);
        LoanInterestAccrued event =
                LoanInterestAccrued.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setLoanId(id)
                        .setBusinessDate(businessDate)
                        .setAmount(money(amount))
                        .setInstalmentNumber(instalmentNumber)
                        .setAccruedAt(clock.instant())
                        .build();
        append(meta, id, event);
    }

    @Override
    public void repaymentAllocated(
            UUID loanId,
            String loanNumber,
            String repaymentId,
            AllocationResult result,
            UUID causationEventId) {
        String id = loanId.toString();
        EventMeta meta = meta("loan.repayment-allocated", id, causationEventId);
        LoanRepaymentAllocated event =
                LoanRepaymentAllocated.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setLoanId(id)
                        .setRepaymentId(repaymentId)
                        .setTotalAllocated(money(result.allocatedTotal()))
                        .setPenal(money(result.penalTotal()))
                        .setInterest(money(result.interestTotal()))
                        .setPrincipal(money(result.principalTotal()))
                        .setAdvance(money(result.advance()))
                        .setAllocatedAt(clock.instant())
                        .build();
        append(meta, id, event);
    }

    @Override
    public void repaymentReversed(
            UUID loanId,
            String loanNumber,
            String repaymentId,
            Money penal,
            Money interest,
            Money principal,
            UUID causationEventId) {
        String id = loanId.toString();
        EventMeta meta = meta("loan.repayment-reversed", id, causationEventId);
        Money total = penal.add(interest).add(principal);
        LoanRepaymentReversed event =
                LoanRepaymentReversed.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setLoanId(id)
                        .setRepaymentId(repaymentId)
                        .setTotalReversed(money(total))
                        .setPenal(money(penal))
                        .setInterest(money(interest))
                        .setPrincipal(money(principal))
                        .setAdvance(money(Money.zero(total.currency())))
                        .setReversedAt(clock.instant())
                        .build();
        append(meta, id, event);
    }

    @Override
    public void loanForeclosed(
            Loan loan, String repaymentId, Money foreclosureAmount, UUID causationEventId) {
        String loanId = loan.id().toString();
        EventMeta meta = meta("loan.foreclosed", loanId, causationEventId);
        LoanForeclosed event =
                LoanForeclosed.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setLoanId(loanId)
                        .setLoanNumber(loan.loanNumber())
                        .setRepaymentId(repaymentId)
                        .setForeclosureAmount(money(foreclosureAmount))
                        .setForeclosedAt(clock.instant())
                        .build();
        append(meta, loanId, event);
    }

    @Override
    public void loanClosed(Loan loan, String reason, UUID causationEventId) {
        String loanId = loan.id().toString();
        EventMeta meta = meta("loan.closed", loanId, causationEventId);
        LoanClosed event =
                LoanClosed.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setLoanId(loanId)
                        .setLoanNumber(loan.loanNumber())
                        .setReason(reason)
                        .setClosedAt(clock.instant())
                        .build();
        append(meta, loanId, event);
    }

    @Override
    public void loanCancelled(Loan loan, String reason, UUID causationEventId) {
        String loanId = loan.id().toString();
        EventMeta meta = meta("loan.cancelled", loanId, causationEventId);
        LoanCancelled event =
                LoanCancelled.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setLoanId(loanId)
                        .setLoanNumber(loan.loanNumber())
                        .setReason(reason)
                        .setCancelledAt(clock.instant())
                        .build();
        append(meta, loanId, event);
    }

    private EventMeta meta(String eventType, String loanId, UUID causationEventId) {
        return EventMeta.newEvent(
                clock,
                eventType,
                SCHEMA_VERSION,
                AGGREGATE,
                loanId,
                loanId, // correlationId: the loan flow id
                causationEventId,
                RequestContextHolder.current().traceId(),
                PRODUCER);
    }

    private void append(EventMeta meta, String loanId, SpecificRecord event) {
        byte[] payload = serializer.serialize(TOPIC, event);
        OutboxRecord row =
                new OutboxRecord(
                        Uuid7.generate(clock),
                        AGGREGATE,
                        loanId,
                        meta.eventType(),
                        meta.eventId(),
                        TOPIC,
                        loanId.getBytes(StandardCharsets.UTF_8),
                        payload,
                        EventHeaders.toHeaders(meta),
                        0,
                        null,
                        clock.instant(),
                        null);
        outbox.append(row);
    }

    private static MoneyAmount money(Money money) {
        return MoneyAmount.newBuilder()
                .setMinorUnits(money.minorUnits())
                .setCurrency(money.currency().getCurrencyCode())
                .build();
    }
}
