package com.lms.loanaccount.config;

import com.lms.config.BusinessCalendar;
import com.lms.events.AvroEventSerde;
import com.lms.loanaccount.application.AccrueInterestService;
import com.lms.loanaccount.application.ActivateLoanService;
import com.lms.loanaccount.application.AllocateRepaymentService;
import com.lms.loanaccount.application.CancelLoanService;
import com.lms.loanaccount.application.CreateLoanService;
import com.lms.loanaccount.application.DailyLoanJobs;
import com.lms.loanaccount.application.DpdService;
import com.lms.loanaccount.application.DueSoonService;
import com.lms.loanaccount.application.ForecloseLoanService;
import com.lms.loanaccount.application.LoanQueryService;
import com.lms.loanaccount.application.PenalAccrualService;
import com.lms.loanaccount.application.ReverseAllocationService;
import com.lms.loanaccount.application.port.out.AccrualRepository;
import com.lms.loanaccount.application.port.out.AllocationRepository;
import com.lms.loanaccount.application.port.out.DpdSnapshotRepository;
import com.lms.loanaccount.application.port.out.LoanEventPublisher;
import com.lms.loanaccount.application.port.out.LoanRepository;
import com.lms.loanaccount.application.port.out.ProductCatalog;
import com.lms.loanaccount.application.port.out.ScheduleRepository;
import com.lms.loanaccount.application.port.out.StateHistoryRepository;
import com.lms.outbox.OutboxRepository;
import com.lms.outbox.inbox.IdempotentConsumer;
import com.lms.outbox.inbox.InboxStore;
import com.lms.outbox.jdbc.JdbcInboxStore;
import com.lms.outbox.jdbc.JdbcOutboxRepository;
import com.lms.schedule.ReducingBalanceScheduleEngine;
import com.lms.schedule.ScheduleEngine;
import java.time.Clock;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wires loan-account: outbox/inbox, Avro serde, schedule engine and the application services. */
@Configuration
public class LoanAccountConfig {

    @Bean
    public Serializer<Object> avroSerializer(
            @Value("${lms.kafka.schema-registry-url}") String schemaRegistryUrl) {
        return AvroEventSerde.serializer(schemaRegistryUrl, true);
    }

    @Bean
    public OutboxRepository outboxRepository(JdbcClient jdbcClient) {
        return new JdbcOutboxRepository(jdbcClient);
    }

    @Bean
    public InboxStore inboxStore(JdbcClient jdbcClient) {
        return new JdbcInboxStore(jdbcClient);
    }

    @Bean
    public IdempotentConsumer idempotentConsumer(InboxStore inboxStore) {
        return new IdempotentConsumer(inboxStore);
    }

    @Bean
    public ScheduleEngine scheduleEngine() {
        return new ReducingBalanceScheduleEngine();
    }

    @Bean
    public CreateLoanService createLoanService(
            LoanRepository loans,
            ScheduleRepository schedules,
            StateHistoryRepository history,
            ProductCatalog products,
            ScheduleEngine scheduleEngine,
            LoanEventPublisher publisher,
            BusinessCalendar calendar,
            Clock clock) {
        return new CreateLoanService(
                loans, schedules, history, products, scheduleEngine, publisher, calendar, clock);
    }

    @Bean
    public ActivateLoanService activateLoanService(
            LoanRepository loans,
            ScheduleRepository schedules,
            StateHistoryRepository history,
            ProductCatalog products,
            ScheduleEngine scheduleEngine,
            LoanEventPublisher publisher,
            Clock clock) {
        return new ActivateLoanService(
                loans, schedules, history, products, scheduleEngine, publisher, clock);
    }

    @Bean
    public CancelLoanService cancelLoanService(
            LoanRepository loans,
            StateHistoryRepository history,
            LoanEventPublisher publisher,
            Clock clock) {
        return new CancelLoanService(loans, history, publisher, clock);
    }

    @Bean
    public AllocateRepaymentService allocateRepaymentService(
            LoanRepository loans,
            ScheduleRepository schedules,
            AllocationRepository allocations,
            StateHistoryRepository history,
            LoanEventPublisher publisher,
            BusinessCalendar calendar,
            Clock clock) {
        return new AllocateRepaymentService(
                loans, schedules, allocations, history, publisher, calendar, clock);
    }

    @Bean
    public ReverseAllocationService reverseAllocationService(
            LoanRepository loans,
            ScheduleRepository schedules,
            AllocationRepository allocations,
            LoanEventPublisher publisher) {
        return new ReverseAllocationService(loans, schedules, allocations, publisher);
    }

    @Bean
    public ForecloseLoanService forecloseLoanService(
            LoanRepository loans,
            ScheduleRepository schedules,
            AllocationRepository allocations,
            StateHistoryRepository history,
            LoanEventPublisher publisher,
            LoanQueryService loanQueryService,
            AllocateRepaymentService allocateRepaymentService,
            BusinessCalendar calendar,
            Clock clock) {
        return new ForecloseLoanService(
                loans,
                schedules,
                allocations,
                history,
                publisher,
                loanQueryService,
                allocateRepaymentService,
                calendar,
                clock);
    }

    @Bean
    public AccrueInterestService accrueInterestService(
            LoanRepository loans,
            ScheduleRepository schedules,
            AccrualRepository accruals,
            LoanEventPublisher publisher) {
        return new AccrueInterestService(loans, schedules, accruals, publisher);
    }

    @Bean
    public PenalAccrualService penalAccrualService(
            LoanRepository loans,
            ScheduleRepository schedules,
            AccrualRepository accruals,
            ProductCatalog products,
            LoanEventPublisher publisher) {
        return new PenalAccrualService(loans, schedules, accruals, products, publisher);
    }

    @Bean
    public DpdService dpdService(
            LoanRepository loans,
            ScheduleRepository schedules,
            DpdSnapshotRepository snapshots,
            StateHistoryRepository history,
            ProductCatalog products,
            LoanEventPublisher publisher,
            Clock clock) {
        return new DpdService(loans, schedules, snapshots, history, products, publisher, clock);
    }

    @Bean
    public DueSoonService dueSoonService(
            LoanRepository loans,
            ScheduleRepository schedules,
            AccrualRepository accruals,
            LoanEventPublisher publisher,
            @Value("${lms.jobs.pre-due-days:3}") int preDueDays) {
        return new DueSoonService(loans, schedules, accruals, publisher, preDueDays);
    }

    @Bean
    public DailyLoanJobs dailyLoanJobs(
            AccrueInterestService interest,
            PenalAccrualService penal,
            DpdService dpd,
            DueSoonService dueSoon) {
        return new DailyLoanJobs(interest, penal, dpd, dueSoon);
    }

    @Bean
    public LoanQueryService loanQueryService(
            LoanRepository loans,
            ScheduleRepository schedules,
            StateHistoryRepository history,
            ProductCatalog products,
            BusinessCalendar calendar) {
        return new LoanQueryService(loans, schedules, history, products, calendar);
    }
}
