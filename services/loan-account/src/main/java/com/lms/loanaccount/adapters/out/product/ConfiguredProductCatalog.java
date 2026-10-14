package com.lms.loanaccount.adapters.out.product;

import com.lms.loanaccount.application.port.out.ProductCatalog;
import com.lms.money.Money;
import com.lms.money.Rate;
import com.lms.schedule.FixedTenorReducingBalanceProduct;
import com.lms.schedule.Product;
import java.util.Currency;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the active product from configuration (docs/domain-rules.md "The one product": every value
 * is configurable, never a literal). loan-account owns the versioned {@code products} table (seeded
 * by Flyway); for the single v1 product the configured values mirror that seed, and a loan pins the
 * version it was created under.
 */
@Component
public class ConfiguredProductCatalog implements ProductCatalog {

    private final Product product;

    public ConfiguredProductCatalog(
            @Value("${lms.currency.default:INR}") String currencyCode,
            @Value("${lms.product.id:MICRO-V1}") String productId,
            @Value("${lms.product.version:1}") int version,
            @Value("${lms.product.min-principal-minor:500000}") long minPrincipalMinor,
            @Value("${lms.product.max-principal-minor:5000000}") long maxPrincipalMinor,
            @Value("${lms.product.principal-step-minor:50000}") long principalStepMinor,
            @Value("${lms.product.apr-percent:24}") long aprPercent,
            @Value("${lms.product.fee-percent:2}") long feePercent,
            @Value("${lms.product.gst-percent:18}") long gstPercent,
            @Value("${lms.product.penal-apr-percent:36}") long penalAprPercent,
            @Value("${lms.product.grace-days:3}") int graceDays,
            @Value("${lms.product.foreclosure-charge-percent:0}") long foreclosureChargePercent,
            @Value("${lms.product.tenors:3,6,9,12}") List<Integer> tenors) {
        Currency currency = Currency.getInstance(currencyCode);
        this.product =
                new FixedTenorReducingBalanceProduct(
                        productId,
                        version,
                        currency,
                        Money.of(minPrincipalMinor, currency),
                        Money.of(maxPrincipalMinor, currency),
                        Money.of(principalStepMinor, currency),
                        tenors,
                        Rate.percent(aprPercent),
                        Rate.percent(feePercent),
                        Rate.percent(gstPercent),
                        Rate.percent(penalAprPercent),
                        graceDays,
                        Rate.percent(foreclosureChargePercent));
    }

    @Override
    public Product current() {
        return product;
    }

    @Override
    public Product byVersion(String productId, int version) {
        // Single v1 product: the only version is the current one.
        return product;
    }
}
