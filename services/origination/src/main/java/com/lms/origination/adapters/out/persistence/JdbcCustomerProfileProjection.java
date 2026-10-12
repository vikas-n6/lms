package com.lms.origination.adapters.out.persistence;

import com.lms.origination.application.port.out.CustomerProfileProjection;
import com.lms.origination.application.port.out.CustomerProjectionUpdater;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read + write model of customer KYC/bank status, maintained by the customer-events listener.
 * Backed by the {@code customer_projection} table (upserted on each event). Verified in the Docker
 * session.
 */
@Repository
public class JdbcCustomerProfileProjection
        implements CustomerProfileProjection, CustomerProjectionUpdater {

    private final JdbcClient jdbc;

    public JdbcCustomerProfileProjection(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Snapshot> find(String customerId) {
        return jdbc.sql(
                        "select kyc_verified, bank_account_linked from customer_projection where customer_id = :id")
                .param("id", customerId)
                .query(
                        (rs, n) ->
                                new Snapshot(
                                        rs.getBoolean("kyc_verified"),
                                        rs.getBoolean("bank_account_linked")))
                .optional();
    }

    @Override
    public void markKycVerified(String customerId) {
        upsert(customerId, "kyc_verified");
    }

    @Override
    public void markBankLinked(String customerId) {
        upsert(customerId, "bank_account_linked");
    }

    private void upsert(String customerId, String column) {
        jdbc.sql(
                        "insert into customer_projection (customer_id, "
                                + column
                                + ", updated_at) values (:id, true, now()) "
                                + "on conflict (customer_id) do update set "
                                + column
                                + " = true, updated_at = now()")
                .param("id", customerId)
                .update();
    }
}
