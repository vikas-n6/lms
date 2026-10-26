package com.lms.loanaccount.application.port.out;

import com.lms.loanaccount.domain.WriteOffRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for two-person write-off requests (phase prompt task 2). Rows are mutable
 * (REQUESTED → APPROVED/REJECTED) with optimistic locking on {@link WriteOffRequest#version()}.
 */
public interface WriteOffRequestRepository {

    void insert(WriteOffRequest request);

    void update(WriteOffRequest request);

    Optional<WriteOffRequest> findById(UUID id);

    List<WriteOffRequest> findByLoanId(UUID loanId);

    /** All requests, newest first, optionally filtered by status (for the admin queue). */
    List<WriteOffRequest> findAll(Optional<String> status);
}
