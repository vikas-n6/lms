package com.lms.customer.adapters.out.persistence;

import com.lms.customer.application.port.out.ConsentRepository;
import com.lms.customer.domain.Consent;
import com.lms.customer.domain.ConsentLedger;
import com.lms.customer.domain.ConsentType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Append-only JDBC consent store (never updated/deleted; CLAUDE.md). */
@Repository
public class JdbcConsentRepository implements ConsentRepository {

    private final JdbcClient jdbc;

    public JdbcConsentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(String customerId, ConsentType type, Instant grantedAt) {
        jdbc.sql(
                        "insert into consents (id, customer_id, type, granted_at) "
                                + "values (:id, :cust, :type, :at)")
                .param("id", UUID.randomUUID())
                .param("cust", customerId)
                .param("type", type.name())
                .param("at", java.sql.Timestamp.from(grantedAt))
                .update();
    }

    @Override
    public ConsentLedger ledgerOf(String customerId) {
        List<Consent> entries =
                jdbc.sql(
                                "select type, granted_at from consents where customer_id = :cust order by granted_at")
                        .param("cust", customerId)
                        .query(
                                (rs, n) ->
                                        new Consent(
                                                ConsentType.valueOf(rs.getString("type")),
                                                rs.getTimestamp("granted_at").toInstant()))
                        .list();
        return new ConsentLedger(entries);
    }
}
