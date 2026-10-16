package com.lms.disbursement.domain;

/**
 * How a rail result (callback or status-query answer) is treated against the current disbursement
 * state (phase prompt acceptance tests).
 *
 * <ul>
 *   <li>{@code APPLIED} — a first result for an in-flight disbursement; it drives the state.
 *   <li>{@code DUPLICATE} — the same terminal result arriving again; acknowledged and ignored, so
 *       three duplicate success callbacks still yield exactly one {@code disbursement.succeeded}.
 *   <li>{@code CONFLICT} — a <em>different</em> terminal result after one is already recorded (e.g.
 *       a failure callback after success); ignored, logged and counted in a conflict metric.
 * </ul>
 */
public enum CallbackResolution {
    APPLIED,
    DUPLICATE,
    CONFLICT
}
