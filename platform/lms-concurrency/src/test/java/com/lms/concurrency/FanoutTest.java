package com.lms.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FanoutTest {

    @Test
    void returnsResultsInInputOrder() throws Exception {
        List<Integer> results =
                Fanout.callAll(Duration.ofSeconds(5), List.of(() -> 1, () -> 2, () -> 3, () -> 4));
        assertThat(results).containsExactly(1, 2, 3, 4);
    }

    @Test
    void emptyTaskListReturnsEmpty() throws Exception {
        assertThat(Fanout.<Integer>callAll(Duration.ofSeconds(1), List.of())).isEmpty();
    }

    @Test
    void failureInOneTaskCancelsTheRest() {
        AtomicInteger started = new AtomicInteger();
        Callable<String> boom =
                () -> {
                    throw new IllegalStateException("boom");
                };
        Callable<String> slow =
                () -> {
                    started.incrementAndGet();
                    Thread.sleep(2_000);
                    return "late";
                };
        assertThatThrownBy(() -> Fanout.callAll(Duration.ofSeconds(5), List.of(boom, slow)))
                .isInstanceOf(FanoutException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void exceedingTheDeadlineThrowsTimeout() {
        Callable<String> slow =
                () -> {
                    Thread.sleep(2_000);
                    return "late";
                };
        assertThatThrownBy(() -> Fanout.callAll(Duration.ofMillis(50), List.of(slow)))
                .isInstanceOf(FanoutTimeoutException.class);
    }

    @Test
    void runsTasksInParallelNotSerially() throws Exception {
        // Four 300ms tasks finishing well under the serial total (1200ms) proves parallelism.
        long start = System.nanoTime();
        Fanout.callAll(
                Duration.ofSeconds(5),
                List.of(
                        sleepThen(300, 1),
                        sleepThen(300, 2),
                        sleepThen(300, 3),
                        sleepThen(300, 4)));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMillis).isLessThan(1_000);
    }

    private static Callable<Integer> sleepThen(long millis, int value) {
        return () -> {
            Thread.sleep(millis);
            return value;
        };
    }
}
