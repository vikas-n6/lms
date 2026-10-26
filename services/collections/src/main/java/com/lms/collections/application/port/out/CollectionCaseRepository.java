package com.lms.collections.application.port.out;

import com.lms.collections.domain.CollectionCase;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for collection cases (phase prompt task 4). Mutable, optimistic-locked. */
public interface CollectionCaseRepository {

    void insert(CollectionCase c);

    void update(CollectionCase c);

    Optional<CollectionCase> findById(UUID id);

    Optional<CollectionCase> findByLoanId(String loanId);

    /** Cases filtered by bucket, status and/or agent (the admin queue). */
    List<CollectionCase> find(
            Optional<String> bucket, Optional<String> status, Optional<String> agent);
}
