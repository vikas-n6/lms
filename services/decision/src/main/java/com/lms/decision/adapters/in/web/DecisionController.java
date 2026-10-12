package com.lms.decision.adapters.in.web;

import com.lms.decision.application.DecisionQueryService;
import com.lms.decision.application.DecisionService;
import com.lms.decision.domain.Decision;
import com.lms.decision.domain.Outcome;
import com.lms.money.Money;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for decisions (phase prompt S5). Role enforcement (admin-only review) is applied at
 * the gateway. {@code GET /decisions/{id}/explain} returns the human-readable breakdown; {@code
 * POST /decisions/{id}/review} records an admin override and republishes decision.completed.
 */
@RestController
@RequestMapping("/decisions")
public class DecisionController {

    private final DecisionQueryService query;
    private final DecisionService decisions;

    public DecisionController(DecisionQueryService query, DecisionService decisions) {
        this.query = query;
        this.decisions = decisions;
    }

    @GetMapping("/{id}/explain")
    public ExplainResponse explain(@PathVariable String id) {
        return ExplainResponse.from(query.byId(id));
    }

    @PostMapping("/{id}/review")
    public ExplainResponse review(@PathVariable String id, @RequestBody ReviewRequest request) {
        Decision prior = query.byId(id);
        Optional<Money> limit =
                request.offeredLimitMinor() == null
                        ? Optional.empty()
                        : Optional.of(
                                Money.of(
                                        request.offeredLimitMinor(),
                                        prior.requestedAmount().currency()));
        Decision overridden =
                decisions.review(prior.applicationId(), request.outcome(), request.note(), limit);
        return ExplainResponse.from(overridden);
    }

    /** The decision breakdown (inputs, bureau snapshot, rule-by-rule result, reason codes). */
    public record ExplainResponse(
            String decisionId,
            String applicationId,
            String outcome,
            List<String> reasonCodes,
            List<RuleLine> rules,
            String ruleSetVersion,
            Long offeredLimitMinor,
            String currency,
            BureauView bureau,
            boolean manualOverride,
            String reviewNote) {

        record RuleLine(String rule, String status, String reasonCode, String detail) {}

        record BureauView(boolean available, int score, boolean overdue, int activeAccounts) {}

        static ExplainResponse from(Decision d) {
            List<RuleLine> rules =
                    d.ruleResults().stream()
                            .map(
                                    r ->
                                            new RuleLine(
                                                    r.ruleId(),
                                                    r.status().name(),
                                                    r.reasonCode() == null
                                                            ? null
                                                            : r.reasonCode().name(),
                                                    r.detail()))
                            .toList();
            return new ExplainResponse(
                    d.id().toString(),
                    d.applicationId(),
                    d.outcome().name(),
                    d.reasonCodes().stream().map(Enum::name).toList(),
                    rules,
                    d.ruleSetVersion(),
                    d.offeredLimit().map(Money::minorUnits).orElse(null),
                    d.requestedAmount().currency().getCurrencyCode(),
                    new BureauView(
                            d.bureauSnapshot().available(),
                            d.bureauSnapshot().score(),
                            d.bureauSnapshot().overdue(),
                            d.bureauSnapshot().activeAccounts()),
                    d.manualOverride(),
                    d.reviewNote().orElse(null));
        }
    }

    /** Admin override request. */
    public record ReviewRequest(@NotNull Outcome outcome, String note, Long offeredLimitMinor) {}
}
