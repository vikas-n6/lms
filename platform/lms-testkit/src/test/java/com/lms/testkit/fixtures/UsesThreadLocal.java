package com.lms.testkit.fixtures;

/** Deliberately violates the ThreadLocal ban, to prove the ArchUnit rule catches it. */
public final class UsesThreadLocal {

    private final ThreadLocal<String> holder = new ThreadLocal<>();

    public void set(String value) {
        holder.set(value);
    }

    public String get() {
        return holder.get();
    }
}
