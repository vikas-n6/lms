package com.lms.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LmsConfigAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(LmsConfigAutoConfiguration.class));

    @Test
    void providesAUtcClockByDefault() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(Clock.class);
                    assertThat(context.getBean(Clock.class).getZone())
                            .isEqualTo(java.time.ZoneOffset.UTC);
                });
    }

    @Test
    void defaultsCurrencyToInr() {
        runner.run(
                context -> {
                    CurrencyProperties properties = context.getBean(CurrencyProperties.class);
                    assertThat(properties.defaultCode()).isEqualTo("INR");
                    assertThat(properties.defaultCurrency()).isEqualTo(Currency.getInstance("INR"));
                });
    }

    @Test
    void currencyIsConfigurable() {
        runner.withPropertyValues("lms.currency.default=JPY")
                .run(
                        context ->
                                assertThat(
                                                context.getBean(CurrencyProperties.class)
                                                        .defaultCurrency())
                                        .isEqualTo(Currency.getInstance("JPY")));
    }

    @Test
    void anApplicationSuppliedClockWins() {
        Clock fixed = Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC);
        runner.withBean(Clock.class, () -> fixed)
                .run(context -> assertThat(context.getBean(Clock.class)).isSameAs(fixed));
    }
}
