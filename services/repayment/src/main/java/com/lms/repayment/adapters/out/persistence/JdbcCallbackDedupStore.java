package com.lms.repayment.adapters.out.persistence;

import com.lms.repayment.application.port.out.CallbackDedupStore;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * De-duplicates provider callbacks on {@code (provider_reference, status)} using the DB unique key
 * as the final guard. {@code on conflict do nothing} returns 0 rows for a duplicate.
 */
@Repository
public class JdbcCallbackDedupStore implements CallbackDedupStore {

    private final JdbcClient jdbc;

    public JdbcCallbackDedupStore(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public boolean firstSeen(String providerReference, String status) {
        int inserted =
                jdbc.sql(
                                """
                                insert into repayment_callbacks (provider_reference, status)
                                values (:ref, :status)
                                on conflict (provider_reference, status) do nothing
                                """)
                        .param("ref", providerReference)
                        .param("status", status)
                        .update();
        return inserted == 1;
    }
}
