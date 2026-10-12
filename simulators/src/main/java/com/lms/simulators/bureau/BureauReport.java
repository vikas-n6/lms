package com.lms.simulators.bureau;

/**
 * A bureau pull result.
 *
 * @param score 300..900
 * @param activeAccounts number of active credit accounts
 * @param overdue true when an active overdue account is reported
 */
public record BureauReport(int score, int activeAccounts, boolean overdue) {}
