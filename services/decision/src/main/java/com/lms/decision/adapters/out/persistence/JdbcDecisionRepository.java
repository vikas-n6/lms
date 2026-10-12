package com.lms.decision.adapters.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.decision.application.port.out.DecisionRepository;
import com.lms.decision.domain.BureauSnapshot;
import com.lms.decision.domain.Decision;
import com.lms.decision.domain.DecisionId;
import com.lms.decision.domain.DecisionInputs;
import com.lms.decision.domain.Outcome;
import com.lms.decision.domain.ReasonCode;
import com.lms.decision.domain.RuleResult;
import com.lms.money.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Spring JDBC persistence for decisions (no JPA). Scalar fields are columns; the inputs snapshot,
 * per-rule results and reason codes are JSONB so the full explainable/replayable record is stored
 * (docs/domain-rules.md). The bureau snapshot is a separate owned table. Verified against Postgres
 * in the Docker session.
 */
@Repository
public class JdbcDecisionRepository implements DecisionRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public JdbcDecisionRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void save(Decision d) {
        jdbc.sql(
                        """
                        insert into decisions
                          (id, application_id, customer_id, requested_minor, currency, outcome,
                           reason_codes, rule_results, inputs, offered_limit_minor, rule_set_version,
                           manual_override, review_note, decided_at, created_at, updated_at, version)
                        values
                          (:id, :appId, :custId, :reqMinor, :ccy, :outcome,
                           :reasons::jsonb, :ruleResults::jsonb, :inputs::jsonb, :offered, :rsv,
                           :manual, :note, :decidedAt, :decidedAt, :decidedAt, 0)
                        """)
                .param("id", d.id().value())
                .param("appId", d.applicationId())
                .param("custId", d.customerId())
                .param("reqMinor", d.requestedAmount().minorUnits())
                .param("ccy", d.requestedAmount().currency().getCurrencyCode())
                .param("outcome", d.outcome().name())
                .param("reasons", toJson(d.reasonCodes()))
                .param("ruleResults", toJson(d.ruleResults()))
                .param("inputs", toJson(d.inputs()))
                .param("offered", d.offeredLimit().map(Money::minorUnits).orElse(null))
                .param("rsv", d.ruleSetVersion())
                .param("manual", d.manualOverride())
                .param("note", d.reviewNote().orElse(null))
                .param("decidedAt", java.sql.Timestamp.from(d.decidedAt()))
                .update();

        BureauSnapshot b = d.bureauSnapshot();
        jdbc.sql(
                        """
                        insert into bureau_snapshots
                          (decision_id, available, score, overdue, active_accounts,
                           bank_income_minor, currency, created_at)
                        values (:id, :avail, :score, :overdue, :accts, :income, :ccy, :createdAt)
                        """)
                .param("id", d.id().value())
                .param("avail", b.available())
                .param("score", b.score())
                .param("overdue", b.overdue())
                .param("accts", b.activeAccounts())
                .param("income", b.bankEstimatedIncome().map(Money::minorUnits).orElse(null))
                .param("ccy", d.requestedAmount().currency().getCurrencyCode())
                .param("createdAt", java.sql.Timestamp.from(d.decidedAt()))
                .update();
    }

    @Override
    public Optional<Decision> findById(DecisionId id) {
        return jdbc.sql(decisionSelect() + " where d.id = :id")
                .param("id", id.value())
                .query(this::mapRow)
                .optional();
    }

    @Override
    public Optional<Decision> findLatestByApplication(String applicationId) {
        return jdbc.sql(
                        decisionSelect()
                                + " where d.application_id = :appId order by d.decided_at desc limit 1")
                .param("appId", applicationId)
                .query(this::mapRow)
                .optional();
    }

    private static String decisionSelect() {
        return """
            select d.id, d.application_id, d.customer_id, d.requested_minor, d.currency, d.outcome,
                   d.reason_codes, d.rule_results, d.inputs, d.offered_limit_minor, d.rule_set_version,
                   d.manual_override, d.review_note, d.decided_at,
                   b.available, b.score, b.overdue, b.active_accounts, b.bank_income_minor
            from decisions d join bureau_snapshots b on b.decision_id = d.id
            """;
    }

    private Decision mapRow(ResultSet rs, int rowNum) throws SQLException {
        Currency ccy = Currency.getInstance(rs.getString("currency"));
        Long offered = (Long) rs.getObject("offered_limit_minor");
        Long bankIncome = (Long) rs.getObject("bank_income_minor");
        BureauSnapshot bureau =
                new BureauSnapshot(
                        rs.getBoolean("available"),
                        rs.getInt("score"),
                        rs.getBoolean("overdue"),
                        rs.getInt("active_accounts"),
                        bankIncome == null
                                ? Optional.empty()
                                : Optional.of(Money.of(bankIncome, ccy)));
        return new Decision(
                new DecisionId(rs.getObject("id", java.util.UUID.class)),
                rs.getString("application_id"),
                rs.getString("customer_id"),
                Money.of(rs.getLong("requested_minor"), ccy),
                Outcome.valueOf(rs.getString("outcome")),
                fromJson(rs.getString("reason_codes"), new TypeReference<List<ReasonCode>>() {}),
                fromJson(rs.getString("rule_results"), new TypeReference<List<RuleResult>>() {}),
                fromJson(rs.getString("inputs"), new TypeReference<DecisionInputs>() {}),
                bureau,
                offered == null ? Optional.empty() : Optional.of(Money.of(offered, ccy)),
                rs.getString("rule_set_version"),
                rs.getBoolean("manual_override"),
                Optional.ofNullable(rs.getString("review_note")),
                rs.getTimestamp("decided_at").toInstant());
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize decision field", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("failed to deserialize decision field", e);
        }
    }
}
