package com.lms.origination.adapters.out.product;

import com.lms.money.Money;
import com.lms.money.Rate;
import com.lms.origination.application.port.out.ProductCatalog;
import com.lms.schedule.FixedTenorReducingBalanceProduct;
import com.lms.schedule.Product;
import java.util.Currency;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the active product from configuration (docs/domain-rules.md "The one product": all values
 * are configurable defaults). The JDBC-backed versioned {@code products} table arrives with
 * loan-account in Phase 2; here the values come from properties with the domain-rules defaults.
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
            @Value("${lms.product.grace-days:3}") int graceDays) {
        Currency currency = Currency.getInstance(currencyCode);
        this.product =
                new FixedTenorReducingBalanceProduct(
                        productId,
                        version,
                        currency,
                        Money.of(minPrincipalMinor, currency),
                        Money.of(maxPrincipalMinor, currency),
                        Money.of(principalStepMinor, currency),
                        List.of(3, 6, 9, 12),
                        Rate.percent(aprPercent),
                        Rate.percent(feePercent),
                        Rate.percent(gstPercent),
                        Rate.percent(penalAprPercent),
                        graceDays,
                        Rate.zero());
    }

    @Override
    public Product current() {
        return product;
    }
}
