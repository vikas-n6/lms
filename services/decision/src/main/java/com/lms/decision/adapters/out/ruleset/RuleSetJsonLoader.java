package com.lms.decision.adapters.out.ruleset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.decision.domain.LimitPolicy;
import com.lms.decision.domain.Rule;
import com.lms.decision.domain.RuleSet;
import com.lms.money.Money;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/**
 * Parses a versioned rule set from JSON into the typed domain {@link RuleSet} (phase prompt S5).
 * The JSON is the system of record for every threshold, band and multiple — nothing is hard-coded
 * in Java (CLAUDE.md global do-not list). All money thresholds use the rule set's single currency.
 */
public class RuleSetJsonLoader {

    private final ObjectMapper mapper;

    public RuleSetJsonLoader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public RuleSet load(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            String version = root.get("version").asText();
            Currency currency = Currency.getInstance(root.get("currency").asText());
            List<Rule> rules = new ArrayList<>();
            for (JsonNode node : root.get("rules")) {
                rules.add(toRule(node, currency));
            }
            LimitPolicy limitPolicy = toLimitPolicy(root.get("limitPolicy"), currency);
            return new RuleSet(version, rules, limitPolicy);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("invalid rule set JSON", e);
        }
    }

    private static Rule toRule(JsonNode node, Currency currency) {
        String type = node.get("type").asText();
        return switch (type) {
            case "AGE" -> new Rule.AgeRule(node.get("minAge").asInt(), node.get("maxAge").asInt());
            case "KYC" -> new Rule.KycRule();
            case "BUREAU_SCORE" ->
                    new Rule.BureauScoreRule(
                            node.get("minApprove").asInt(), node.get("minRefer").asInt());
            case "BUREAU_DELINQUENCY" -> new Rule.BureauDelinquencyRule();
            case "INCOME" ->
                    new Rule.IncomeRule(
                            Money.of(node.get("minMonthlyIncomeMinor").asLong(), currency));
            case "AFFORDABILITY" -> new Rule.AffordabilityRule(node.get("maxFoirPercent").asInt());
            case "ACTIVE_LOANS" -> new Rule.ActiveLoanRule(node.get("maxActiveLoans").asInt());
            case "VELOCITY" -> new Rule.VelocityRule(node.get("maxPerWindow").asInt());
            default -> throw new IllegalArgumentException("unknown rule type: " + type);
        };
    }

    private static LimitPolicy toLimitPolicy(JsonNode node, Currency currency) {
        int incomeMultiple = node.get("incomeMultiple").asInt();
        List<LimitPolicy.Band> bands = new ArrayList<>();
        for (JsonNode band : node.get("bands")) {
            bands.add(
                    new LimitPolicy.Band(
                            band.get("minScore").asInt(),
                            band.get("maxScore").asInt(),
                            Money.of(band.get("maxAmountMinor").asLong(), currency)));
        }
        return new LimitPolicy(bands, incomeMultiple);
    }
}
