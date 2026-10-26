package com.lms.collections.adapters.out.persistence;

import com.lms.collections.application.port.out.DunningActionRepository;
import com.lms.collections.domain.DunningAction;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Spring JDBC persistence for dunning actions. The unique {@code (case_id, step_id)} makes {@link
 * #recordIfAbsent} fire each step at most once; a duplicate insert is caught and reported as "not
 * inserted" so replays and duplicate ticks never repeat the action (phase prompt task 4).
 */
@Repository
public class JdbcDunningActionRepository implements DunningActionRepository {

    private final JdbcClient jdbc;

    public JdbcDunningActionRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public boolean recordIfAbsent(DunningAction a) {
        try {
            jdbc.sql(
                            """
                            insert into dunning_actions (id, case_id, step_id, dpd_threshold,
                                action_type, channel, template_id, fired_at)
                            values (:id, :caseId, :stepId, :threshold, :actionType, :channel,
                                :templateId, :firedAt)
                            """)
                    .param("id", a.id())
                    .param("caseId", a.caseId())
                    .param("stepId", a.stepId())
                    .param("threshold", a.dpdThreshold())
                    .param("actionType", a.actionType())
                    .param("channel", a.channel())
                    .param("templateId", a.templateId())
                    .param("firedAt", java.sql.Timestamp.from(a.firedAt()))
                    .update();
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false; // already fired for this (caseId, stepId): idempotent
        }
    }

    @Override
    public List<DunningAction> findByCaseId(UUID caseId) {
        return jdbc.sql(
                        """
                        select id, case_id, step_id, dpd_threshold, action_type, channel,
                               template_id, fired_at
                          from dunning_actions where case_id = :caseId order by fired_at
                        """)
                .param("caseId", caseId)
                .query(JdbcDunningActionRepository::mapRow)
                .list();
    }

    private static DunningAction mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new DunningAction(
                rs.getObject("id", UUID.class),
                rs.getObject("case_id", UUID.class),
                rs.getString("step_id"),
                rs.getInt("dpd_threshold"),
                rs.getString("action_type"),
                rs.getString("channel"),
                rs.getString("template_id"),
                rs.getTimestamp("fired_at").toInstant());
    }
}
