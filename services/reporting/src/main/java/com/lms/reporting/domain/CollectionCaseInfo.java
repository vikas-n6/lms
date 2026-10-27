package com.lms.reporting.domain;

import java.time.Instant;
import java.util.UUID;

/** A collection case projection from collections.events, for the ops/collection views. */
public record CollectionCaseInfo(
        UUID loanId, String caseId, String bucket, String status, Instant openedAt) {}
