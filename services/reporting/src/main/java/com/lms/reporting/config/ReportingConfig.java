package com.lms.reporting.config;

import com.lms.config.BusinessCalendar;
import com.lms.outbox.inbox.IdempotentConsumer;
import com.lms.outbox.inbox.InboxStore;
import com.lms.outbox.jdbc.JdbcInboxStore;
import com.lms.reporting.application.ApplicationProjection;
import com.lms.reporting.application.AuditProjection;
import com.lms.reporting.application.CollectionCaseProjection;
import com.lms.reporting.application.LoanProjection;
import com.lms.reporting.application.ReconStatusProjection;
import com.lms.reporting.application.ReportingQueryService;
import com.lms.reporting.application.SnapshotService;
import com.lms.reporting.application.port.out.ApplicationReadRepository;
import com.lms.reporting.application.port.out.AuditLogRepository;
import com.lms.reporting.application.port.out.CollectionCaseRepository;
import com.lms.reporting.application.port.out.CollectionCounterRepository;
import com.lms.reporting.application.port.out.DailySnapshotRepository;
import com.lms.reporting.application.port.out.LoanReadRepository;
import com.lms.reporting.application.port.out.ReconStatusRepository;
import java.time.Clock;
import java.util.Currency;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wires the reporting service: inbox idempotency, the event projections and the read queries. */
@Configuration
public class ReportingConfig {

    @Bean
    public Currency reportingCurrency(@Value("${lms.currency.default:INR}") String currencyCode) {
        return Currency.getInstance(currencyCode);
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
    public LoanProjection loanProjection(
            LoanReadRepository loans,
            CollectionCounterRepository counters,
            ApplicationReadRepository applications) {
        return new LoanProjection(loans, counters, applications);
    }

    @Bean
    public ApplicationProjection applicationProjection(ApplicationReadRepository applications) {
        return new ApplicationProjection(applications);
    }

    @Bean
    public AuditProjection auditProjection(AuditLogRepository auditLog) {
        return new AuditProjection(auditLog);
    }

    @Bean
    public CollectionCaseProjection collectionCaseProjection(CollectionCaseRepository cases) {
        return new CollectionCaseProjection(cases);
    }

    @Bean
    public ReconStatusProjection reconStatusProjection(ReconStatusRepository reconStatus) {
        return new ReconStatusProjection(reconStatus);
    }

    @Bean
    public ReportingQueryService reportingQueryService(
            LoanReadRepository loans,
            ApplicationReadRepository applications,
            CollectionCounterRepository counters,
            AuditLogRepository auditLog,
            ReconStatusRepository reconStatus,
            CollectionCaseRepository cases,
            Currency reportingCurrency) {
        return new ReportingQueryService(
                loans, applications, counters, auditLog, reconStatus, cases, reportingCurrency);
    }

    @Bean
    public SnapshotService snapshotService(
            LoanReadRepository loans,
            DailySnapshotRepository snapshots,
            BusinessCalendar calendar,
            Clock clock,
            Currency reportingCurrency) {
        return new SnapshotService(loans, snapshots, calendar, clock, reportingCurrency);
    }
}
