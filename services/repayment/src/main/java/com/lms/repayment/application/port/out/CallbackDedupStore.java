package com.lms.repayment.application.port.out;

/**
 * De-duplicates provider callbacks on {@code (providerReference, status)} (docs/domain-rules.md:
 * rail/mandate callbacks keyed by the provider reference, processed once). The DB unique key is the
 * final guard; a Redis fast-path may sit in front. Implemented by a JDBC/Redis adapter.
 */
public interface CallbackDedupStore {

    /** Record the (reference, status) pair; true if newly seen, false if a duplicate. */
    boolean firstSeen(String providerReference, String status);
}
