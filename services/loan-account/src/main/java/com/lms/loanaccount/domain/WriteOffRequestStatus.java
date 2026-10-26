package com.lms.loanaccount.domain;

/**
 * The lifecycle of a two-person write-off request (phase prompt task 2). A request is raised {@code
 * REQUESTED} by one admin and then either {@code APPROVED} by a different admin (which writes the
 * loan off) or {@code REJECTED}.
 */
public enum WriteOffRequestStatus {
    REQUESTED,
    APPROVED,
    REJECTED
}
