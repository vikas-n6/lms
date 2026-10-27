package com.lms.loanaccount.adapters.out.persistence;

import com.lms.loanaccount.application.ProductVersion;
import com.lms.loanaccount.application.port.out.ProductRepository;
import com.lms.money.Money;
import com.lms.money.Rate;
import com.lms.schedule.FixedTenorReducingBalanceProduct;
import com.lms.schedule.Product;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Spring JDBC persistence for the versioned {@code products} table. Rows are append-only. */
@Repository
public class JdbcProductRepository implements ProductRepository {

    private static final String SELECT =
            """
            select product_id, version, currency, min_principal_minor, max_principal_minor,
                   principal_step_minor, tenors, apr_percent, fee_percent, gst_percent,
                   penal_apr_percent, grace_days, foreclosure_charge_percent, effective_from,
                   created_at
            from products
            """;

    private final JdbcClient jdbc;

    public JdbcProductRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public List<ProductVersion> listVersions(String productId) {
        return jdbc.sql(SELECT + " where product_id = :id order by version desc")
                .param("id", productId)
                .query(JdbcProductRepository::mapVersion)
                .list();
    }

    @Override
    public Optional<Product> findVersion(String productId, int version) {
        return jdbc.sql(SELECT + " where product_id = :id and version = :version")
                .param("id", productId)
                .param("version", version)
                .query(JdbcProductRepository::mapVersion)
                .optional()
                .map(ProductVersion::product);
    }

    @Override
    public Optional<ProductVersion> latestAsOf(String productId, LocalDate asOf) {
        return jdbc.sql(
                        SELECT
                                + " where product_id = :id and effective_from <= :asOf"
                                + " order by version desc limit 1")
                .param("id", productId)
                .param("asOf", asOf)
                .query(JdbcProductRepository::mapVersion)
                .optional();
    }

    @Override
    public int nextVersion(String productId) {
        Integer max =
                jdbc.sql("select max(version) from products where product_id = :id")
                        .param("id", productId)
                        .query(Integer.class)
                        .optional()
                        .orElse(0);
        return (max == null ? 0 : max) + 1;
    }

    @Override
    public void insert(Product p, LocalDate effectiveFrom) {
        String tenors =
                String.join(",", p.tenors().stream().map(String::valueOf).toArray(String[]::new));
        jdbc.sql(
                        """
                        insert into products (product_id, version, currency, min_principal_minor,
                            max_principal_minor, principal_step_minor, tenors, apr_percent,
                            fee_percent, gst_percent, penal_apr_percent, grace_days,
                            foreclosure_charge_percent, effective_from, created_at, updated_at,
                            version_row)
                        values (:id, :version, :currency, :min, :max, :step, :tenors, :apr, :fee,
                            :gst, :penal, :grace, :foreclosure, :effectiveFrom, now(), now(), 0)
                        """)
                .param("id", p.productId())
                .param("version", p.version())
                .param("currency", p.currency().getCurrencyCode())
                .param("min", p.minPrincipal().minorUnits())
                .param("max", p.maxPrincipal().minorUnits())
                .param("step", p.principalStep().minorUnits())
                .param("tenors", tenors)
                .param("apr", p.annualInterestRate().asPlainString())
                .param("fee", p.processingFeeRate().asPlainString())
                .param("gst", p.gstRate().asPlainString())
                .param("penal", p.penalAnnualRate().asPlainString())
                .param("grace", p.graceDays())
                .param("foreclosure", p.foreclosureChargeRate().asPlainString())
                .param("effectiveFrom", effectiveFrom)
                .update();
    }

    private static ProductVersion mapVersion(ResultSet rs, int rowNum) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        List<Integer> tenors =
                Arrays.stream(rs.getString("tenors").split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Integer::valueOf)
                        .toList();
        Product product =
                new FixedTenorReducingBalanceProduct(
                        rs.getString("product_id"),
                        rs.getInt("version"),
                        currency,
                        Money.of(rs.getLong("min_principal_minor"), currency),
                        Money.of(rs.getLong("max_principal_minor"), currency),
                        Money.of(rs.getLong("principal_step_minor"), currency),
                        tenors,
                        Rate.percent(rs.getString("apr_percent")),
                        Rate.percent(rs.getString("fee_percent")),
                        Rate.percent(rs.getString("gst_percent")),
                        Rate.percent(rs.getString("penal_apr_percent")),
                        rs.getInt("grace_days"),
                        Rate.percent(rs.getString("foreclosure_charge_percent")));
        return new ProductVersion(
                product,
                rs.getObject("effective_from", LocalDate.class),
                rs.getTimestamp("created_at").toInstant());
    }
}
