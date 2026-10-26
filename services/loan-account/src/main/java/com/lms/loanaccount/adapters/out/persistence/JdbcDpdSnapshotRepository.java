package com.lms.loanaccount.adapters.out.persistence;

import com.lms.loanaccount.application.port.out.DpdSnapshotRepository;
import com.lms.loanaccount.domain.DpdSnapshot;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Spring JDBC persistence for the per-loan DPD snapshot (phase prompt task 1). A mutable single-row
 * cache per loan (not a ledger of balances), upserted each business date; it exists only so the DPD
 * job is idempotent per {@code (loanId, businessDate)} and can publish once more when DPD clears.
 */
@Repository
public class JdbcDpdSnapshotRepository implements DpdSnapshotRepository {

    private final JdbcClient jdbc;

    public JdbcDpdSnapshotRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<DpdSnapshot> find(UUID loanId) {
        return jdbc.sql(
                        """
                        select loan_id, business_date, days_past_due, bucket
                          from loan_dpd where loan_id = :loanId
                        """)
                .param("loanId", loanId)
                .query(
                        (rs, rowNum) ->
                                new DpdSnapshot(
                                        UUID.fromString(rs.getString("loan_id")),
                                        rs.getObject("business_date", java.time.LocalDate.class),
                                        rs.getInt("days_past_due"),
                                        rs.getString("bucket")))
                .optional();
    }

    @Override
    public void upsert(DpdSnapshot snapshot) {
        jdbc.sql(
                        """
                        insert into loan_dpd (loan_id, business_date, days_past_due, bucket, updated_at)
                        values (:loanId, :date, :dpd, :bucket, now())
                        on conflict (loan_id) do update set
                            business_date = excluded.business_date,
                            days_past_due = excluded.days_past_due,
                            bucket = excluded.bucket,
                            updated_at = now()
                        """)
                .param("loanId", snapshot.loanId())
                .param("date", snapshot.businessDate())
                .param("dpd", snapshot.daysPastDue())
                .param("bucket", snapshot.bucket())
                .update();
    }
}
