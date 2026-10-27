package com.lms.reporting.adapters.out.persistence;

import com.lms.reporting.application.port.out.ApplicationReadRepository;
import com.lms.reporting.domain.ApplicationFunnelRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Spring JDBC store for {@code application_read}. {@code merge} OR-combines stage flags: a flag
 * only ever moves false→true, so projection is order-independent and idempotent on redelivery.
 */
@Repository
public class JdbcApplicationReadRepository implements ApplicationReadRepository {

    private final JdbcClient jdbc;

    public JdbcApplicationReadRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void merge(String applicationId, String customerId, ApplicationFunnelRow flags) {
        jdbc.sql(
                        """
                        insert into application_read (application_id, customer_id, submitted,
                            approved, offered, accepted, disbursed, rejected, expired, created_at,
                            updated_at, version)
                        values (:id, :customerId, :submitted, :approved, :offered, :accepted,
                            :disbursed, :rejected, :expired, now(), now(), 0)
                        on conflict (application_id) do update set
                            customer_id = coalesce(excluded.customer_id, application_read.customer_id),
                            submitted = application_read.submitted or excluded.submitted,
                            approved = application_read.approved or excluded.approved,
                            offered = application_read.offered or excluded.offered,
                            accepted = application_read.accepted or excluded.accepted,
                            disbursed = application_read.disbursed or excluded.disbursed,
                            rejected = application_read.rejected or excluded.rejected,
                            expired = application_read.expired or excluded.expired,
                            updated_at = now(),
                            version = application_read.version + 1
                        """)
                .param("id", applicationId)
                .param("customerId", customerId)
                .param("submitted", flags.submitted())
                .param("approved", flags.approved())
                .param("offered", flags.offered())
                .param("accepted", flags.accepted())
                .param("disbursed", flags.disbursed())
                .param("rejected", flags.rejected())
                .param("expired", flags.expired())
                .update();
    }

    @Override
    public List<ApplicationFunnelRow> findAll() {
        return jdbc.sql(
                        """
                        select application_id, submitted, approved, offered, accepted, disbursed,
                               rejected, expired
                        from application_read
                        """)
                .query(JdbcApplicationReadRepository::mapRow)
                .list();
    }

    private static ApplicationFunnelRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ApplicationFunnelRow(
                rs.getString("application_id"),
                rs.getBoolean("submitted"),
                rs.getBoolean("approved"),
                rs.getBoolean("offered"),
                rs.getBoolean("accepted"),
                rs.getBoolean("disbursed"),
                rs.getBoolean("rejected"),
                rs.getBoolean("expired"));
    }
}
