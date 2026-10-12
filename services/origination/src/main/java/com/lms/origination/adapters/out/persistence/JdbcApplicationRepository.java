package com.lms.origination.adapters.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.money.Money;
import com.lms.origination.application.port.out.ApplicationRepository;
import com.lms.origination.domain.AcceptedTerms;
import com.lms.origination.domain.Application;
import com.lms.origination.domain.ApplicationId;
import com.lms.origination.domain.ApplicationStatus;
import com.lms.origination.domain.Offer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Spring JDBC persistence for applications (no JPA). Scalar draft fields are columns; the offer and
 * accepted terms are JSONB. Each save also appends an immutable application_events audit row. The
 * status-history and event rows are append-only (CLAUDE.md). Verified against Postgres in the
 * Docker session.
 */
@Repository
public class JdbcApplicationRepository implements ApplicationRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public JdbcApplicationRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void save(Application a) {
        jdbc.sql(
                        """
                        insert into applications
                          (id, customer_id, product_id, requested_minor, currency, phone, date_of_birth,
                           declared_income_minor, employment_type, consent_data_processing,
                           consent_credit_pull, consent_loan_agreement, status, offer, accepted_terms,
                           offer_expires_at, created_at, updated_at, version)
                        values
                          (:id, :cust, :prod, :reqMinor, :ccy, :phone, :dob, :income, :emp, :cdp, :ccp,
                           :cla, :status, :offer::jsonb, :terms::jsonb, :offerExpires, :createdAt,
                           :updatedAt, :version)
                        on conflict (id) do update set
                           status = excluded.status, offer = excluded.offer,
                           accepted_terms = excluded.accepted_terms, offer_expires_at = excluded.offer_expires_at,
                           updated_at = excluded.updated_at, version = applications.version + 1
                        """)
                .param("id", a.id().value())
                .param("cust", a.customerId())
                .param("prod", a.productId())
                .param("reqMinor", a.requestedAmount().minorUnits())
                .param("ccy", a.requestedAmount().currency().getCurrencyCode())
                .param("phone", a.phone())
                .param("dob", a.dateOfBirth())
                .param("income", a.declaredMonthlyIncome().minorUnits())
                .param("emp", a.employmentType())
                .param("cdp", a.dataProcessingConsent())
                .param("ccp", a.creditPullConsent())
                .param("cla", a.loanAgreementConsent())
                .param("status", a.status().code())
                .param("offer", a.offer().map(this::toJson).orElse(null))
                .param("terms", a.acceptedTerms().map(this::toJson).orElse(null))
                .param(
                        "offerExpires",
                        a.offer().map(o -> java.sql.Timestamp.from(o.expiresAt())).orElse(null))
                .param("createdAt", java.sql.Timestamp.from(a.createdAt()))
                .param("updatedAt", java.sql.Timestamp.from(a.updatedAt()))
                .param("version", a.version())
                .update();

        jdbc.sql(
                        "insert into application_events (id, application_id, status, at) "
                                + "values (:id, :appId, :status, :at)")
                .param("id", UUID.randomUUID())
                .param("appId", a.id().value())
                .param("status", a.status().code())
                .param("at", java.sql.Timestamp.from(a.updatedAt()))
                .update();
    }

    @Override
    public Optional<Application> findById(ApplicationId id) {
        return jdbc.sql(select() + " where id = :id")
                .param("id", id.value())
                .query(this::mapRow)
                .optional();
    }

    @Override
    public List<Application> list(String statusCodeOrNull, int limit, int offset) {
        var spec =
                statusCodeOrNull == null
                        ? jdbc.sql(
                                select() + " order by created_at desc limit :limit offset :offset")
                        : jdbc.sql(
                                        select()
                                                + " where status = :status order by created_at desc limit :limit offset :offset")
                                .param("status", statusCodeOrNull);
        return spec.param("limit", limit).param("offset", offset).query(this::mapRow).list();
    }

    @Override
    public List<Application> findExpired(Instant now) {
        return jdbc.sql(select() + " where status = 'OFFERED' and offer_expires_at <= :now")
                .param("now", java.sql.Timestamp.from(now))
                .query(this::mapRow)
                .list();
    }

    private static String select() {
        return """
            select id, customer_id, product_id, requested_minor, currency, phone, date_of_birth,
                   declared_income_minor, employment_type, consent_data_processing,
                   consent_credit_pull, consent_loan_agreement, status, offer, accepted_terms,
                   created_at, updated_at, version
            from applications
            """;
    }

    private Application mapRow(ResultSet rs, int rowNum) throws SQLException {
        Currency ccy = Currency.getInstance(rs.getString("currency"));
        String offerJson = rs.getString("offer");
        String termsJson = rs.getString("accepted_terms");
        return new Application(
                new ApplicationId(rs.getObject("id", UUID.class)),
                rs.getString("customer_id"),
                rs.getString("product_id"),
                Money.of(rs.getLong("requested_minor"), ccy),
                rs.getString("phone"),
                rs.getObject("date_of_birth", LocalDate.class),
                Money.of(rs.getLong("declared_income_minor"), ccy),
                rs.getString("employment_type"),
                rs.getBoolean("consent_data_processing"),
                rs.getBoolean("consent_credit_pull"),
                rs.getBoolean("consent_loan_agreement"),
                ApplicationStatus.fromCode(rs.getString("status")),
                offerJson == null
                        ? Optional.empty()
                        : Optional.of(fromJson(offerJson, Offer.class)),
                termsJson == null
                        ? Optional.empty()
                        : Optional.of(fromJson(termsJson, AcceptedTerms.class)),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("version"));
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize application field", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("failed to deserialize application field", e);
        }
    }
}
