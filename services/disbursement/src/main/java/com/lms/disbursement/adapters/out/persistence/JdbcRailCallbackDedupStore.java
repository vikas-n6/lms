package com.lms.disbursement.adapters.out.persistence;

import com.lms.disbursement.application.port.out.RailCallbackDedupStore;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * De-duplicates rail callbacks on {@code (railRef, status)} using the DB unique key as the final
 * guard (docs/architecture.md). A Redis {@code SET NX} fast-path can sit in front of this later;
 * the row insert is authoritative. {@code on conflict do nothing} returns 0 affected rows for a
 * duplicate, so {@code firstSeen} is false exactly when the pair was already recorded.
 */
@Repository
public class JdbcRailCallbackDedupStore implements RailCallbackDedupStore {

    private final JdbcClient jdbc;

    public JdbcRailCallbackDedupStore(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public boolean firstSeen(String railRef, String status) {
        int inserted =
                jdbc.sql(
                                """
                                insert into rail_callbacks (rail_ref, status)
                                values (:railRef, :status)
                                on conflict (rail_ref, status) do nothing
                                """)
                        .param("railRef", railRef)
                        .param("status", status)
                        .update();
        return inserted == 1;
    }
}
