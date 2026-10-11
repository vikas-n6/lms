package com.lms.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LazyTest {

    @Test
    void computesOnFirstAccessOnly() {
        AtomicInteger computations = new AtomicInteger();
        Lazy<String> lazy =
                Lazy.of(
                        () -> {
                            computations.incrementAndGet();
                            return "value";
                        });

        assertThat(lazy.isSet()).isFalse();
        assertThat(lazy.get()).isEqualTo("value");
        assertThat(lazy.get()).isEqualTo("value");
        assertThat(lazy.isSet()).isTrue();
        assertThat(computations).hasValue(1);
    }

    @Test
    void computesAtMostOnceUnderConcurrency() throws Exception {
        AtomicInteger computations = new AtomicInteger();
        Lazy<Integer> lazy =
                Lazy.of(
                        () -> {
                            computations.incrementAndGet();
                            return 42;
                        });

        // Hammer get() from several virtual threads; StableValue must still compute once.
        List<Integer> results =
                Fanout.callAll(
                        java.time.Duration.ofSeconds(5),
                        List.of(lazy::get, lazy::get, lazy::get, lazy::get, lazy::get));

        assertThat(results).containsExactly(42, 42, 42, 42, 42);
        assertThat(computations).hasValue(1);
    }
}
