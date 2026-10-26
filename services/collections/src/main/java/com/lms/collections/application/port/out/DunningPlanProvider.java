package com.lms.collections.application.port.out;

import com.lms.collections.domain.DunningPlan;

/**
 * Supplies the current dunning plan (phase prompt task 4). The plan is versioned configuration
 * ({@code lms.dunning.*}), never hard-coded in Java; a case pins the version it was opened under.
 */
public interface DunningPlanProvider {

    DunningPlan current();
}
