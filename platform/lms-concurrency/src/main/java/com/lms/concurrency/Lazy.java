package com.lms.concurrency;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A lazily initialised, compute-once value backed by {@link StableValue} (a Java 25 preview API).
 * The supplier runs at most once even under concurrent access, and the value is treated as a
 * constant by the JVM afterwards. This is the only sanctioned use of Stable Values in the codebase
 * (CLAUDE.md "Java 25 usage rules"); other modules call it and never import the preview type.
 */
public final class Lazy<T> implements Supplier<T> {

    private final StableValue<T> value = StableValue.of();
    private final Supplier<? extends T> supplier;

    private Lazy(Supplier<? extends T> supplier) {
        this.supplier = supplier;
    }

    public static <T> Lazy<T> of(Supplier<? extends T> supplier) {
        return new Lazy<>(Objects.requireNonNull(supplier, "supplier"));
    }

    /** The value, computing it on first access and returning the memoised result thereafter. */
    @Override
    public T get() {
        return value.orElseSet(supplier);
    }

    /** Whether the value has been computed yet. */
    public boolean isSet() {
        return value.isSet();
    }
}
