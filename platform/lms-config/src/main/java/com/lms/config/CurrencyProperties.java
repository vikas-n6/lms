package com.lms.config;

import java.util.Currency;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

/**
 * Currency configuration. The default currency is {@code lms.currency.default} (INR unless
 * overridden). Currency is a locked decision: it is configurable and never hard-coded, and the
 * minor-unit scale always comes from ISO 4217 (see CLAUDE.md and docs/domain-rules.md).
 *
 * @param defaultCode ISO 4217 code bound from {@code lms.currency.default} ({@code default} is a
 *     Java keyword, so {@link Name} maps the property onto this parameter).
 */
@ConfigurationProperties(prefix = "lms.currency")
public record CurrencyProperties(@Name("default") @DefaultValue("INR") String defaultCode) {

    public CurrencyProperties {
        // Fail fast on an unknown code rather than deep inside a money path.
        Currency.getInstance(defaultCode);
    }

    /** The configured default currency as a {@link Currency}. */
    public Currency defaultCurrency() {
        return Currency.getInstance(defaultCode);
    }
}
