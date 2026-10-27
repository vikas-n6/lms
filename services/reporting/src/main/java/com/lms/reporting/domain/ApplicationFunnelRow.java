package com.lms.reporting.domain;

/**
 * A denormalised application read row carrying the stages one application has reached, built from
 * application.events and decision.events. Stages are cumulative flags (an accepted application was
 * also offered, approved and submitted); {@code rejected}/{@code expired} are terminal negatives.
 */
public record ApplicationFunnelRow(
        String applicationId,
        boolean submitted,
        boolean approved,
        boolean offered,
        boolean accepted,
        boolean disbursed,
        boolean rejected,
        boolean expired) {}
