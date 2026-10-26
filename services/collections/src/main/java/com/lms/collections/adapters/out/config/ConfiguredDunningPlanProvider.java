package com.lms.collections.adapters.out.config;

import com.lms.collections.application.port.out.DunningPlanProvider;
import com.lms.collections.config.DunningPlanProperties;
import com.lms.collections.domain.DunningActionType;
import com.lms.collections.domain.DunningPlan;
import com.lms.collections.domain.DunningStep;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Builds the domain {@link DunningPlan} from {@link DunningPlanProperties} (phase prompt task 4).
 * The plan is configuration; this adapter is the only place the config shape meets the domain.
 */
@Component
public class ConfiguredDunningPlanProvider implements DunningPlanProvider {

    private final DunningPlan plan;

    public ConfiguredDunningPlanProvider(DunningPlanProperties properties) {
        Objects.requireNonNull(properties, "properties");
        this.plan =
                new DunningPlan(
                        properties.version(),
                        properties.steps().stream()
                                .map(
                                        s ->
                                                new DunningStep(
                                                        s.stepId(),
                                                        s.dpdThreshold(),
                                                        DunningActionType.valueOf(s.actionType()),
                                                        s.channel(),
                                                        s.templateId()))
                                .toList());
    }

    @Override
    public DunningPlan current() {
        return plan;
    }
}
