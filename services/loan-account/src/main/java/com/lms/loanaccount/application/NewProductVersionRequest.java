package com.lms.loanaccount.application;

import java.time.LocalDate;
import java.util.List;

/**
 * Parameters for a new product version (phase prompt task 2). All values are configuration, as
 * strings/minor-units so the request carries no {@code Money}/{@code Rate} wire types; the service
 * builds the immutable {@link com.lms.schedule.Product} from them. This versions configuration; it
 * does not change any money rule or the schedule engine.
 */
public record NewProductVersionRequest(
        String currency,
        long minPrincipalMinor,
        long maxPrincipalMinor,
        long principalStepMinor,
        List<Integer> tenors,
        String aprPercent,
        String feePercent,
        String gstPercent,
        String penalAprPercent,
        int graceDays,
        String foreclosureChargePercent,
        LocalDate effectiveFrom) {}
