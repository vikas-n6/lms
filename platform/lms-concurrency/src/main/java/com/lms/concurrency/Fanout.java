package com.lms.concurrency;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;

/**
 * Parallel fan-out over {@link StructuredTaskScope} (a Java 25 preview API). Runs the tasks on
 * virtual threads as a single unit of work: if any task fails or the deadline passes, the rest are
 * cancelled. This is the only sanctioned entry point to structured concurrency in the codebase
 * (CLAUDE.md "Java 25 usage rules"); other modules call it and never import the preview types.
 */
public final class Fanout {

    private Fanout() {}

    /**
     * Run every task in parallel and return their results in the same order as {@code tasks}. All
     * tasks must succeed within {@code timeout}.
     *
     * @throws FanoutException if any task throws (its exception is the cause); remaining tasks are
     *     cancelled
     * @throws FanoutTimeoutException if the tasks do not all finish within {@code timeout}
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public static <T> List<T> callAll(Duration timeout, List<? extends Callable<T>> tasks)
            throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(tasks, "tasks");
        if (tasks.isEmpty()) {
            return List.of();
        }
        try (var scope =
                StructuredTaskScope.open(
                        Joiner.<T>allSuccessfulOrThrow(), cf -> cf.withTimeout(timeout))) {
            List<Subtask<T>> subtasks = new ArrayList<>(tasks.size());
            for (Callable<T> task : tasks) {
                subtasks.add(scope.fork(Objects.requireNonNull(task, "task")));
            }
            scope.join();
            List<T> results = new ArrayList<>(subtasks.size());
            for (Subtask<T> subtask : subtasks) {
                results.add(subtask.get());
            }
            return List.copyOf(results);
        } catch (StructuredTaskScope.FailedException failed) {
            throw new FanoutException(failed.getCause());
        } catch (StructuredTaskScope.TimeoutException timedOut) {
            throw new FanoutTimeoutException(timeout);
        }
    }

    /** Convenience overload taking the tasks as varargs. */
    @SafeVarargs
    public static <T> List<T> callAll(Duration timeout, Callable<T>... tasks)
            throws InterruptedException {
        return callAll(timeout, List.of(tasks));
    }
}
