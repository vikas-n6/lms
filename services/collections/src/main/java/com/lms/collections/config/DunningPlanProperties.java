package com.lms.collections.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The dunning plan as versioned configuration (phase prompt task 4 / CLAUDE.md: the plan is never
 * hard-coded in Java). Bound from {@code lms.dunning.*}; the defaults live in {@code
 * application.yml}.
 *
 * @param version the plan version a new case pins
 * @param steps the ordered steps (dpdThreshold → action)
 */
@ConfigurationProperties(prefix = "lms.dunning")
public record DunningPlanProperties(int version, List<Step> steps) {

    /**
     * @param stepId stable id
     * @param dpdThreshold DPD at or above which the step fires
     * @param actionType {@link com.lms.collections.domain.DunningActionType} name
     * @param channel SMS/EMAIL/NONE
     * @param templateId notification template id
     */
    public record Step(
            String stepId,
            int dpdThreshold,
            String actionType,
            String channel,
            String templateId) {}
}
