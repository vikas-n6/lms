package com.lms.ledger.domain.recon;

import com.lms.money.Money;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pure matcher for the daily bank/rail reconciliation (phase prompt task 6, docs/architecture.md).
 * Matches the external lines (bank statement + rail settlement report) against the account-1001
 * postings by reference and amount and classifies each reference:
 *
 * <ul>
 *   <li>{@code DUPLICATE} — the reference appears more than once on either side;
 *   <li>{@code MATCHED} — present on both sides with equal amounts;
 *   <li>{@code AMOUNT_MISMATCH} — present on both sides with different amounts;
 *   <li>{@code MISSING_IN_LEDGER} — present externally but not in the ledger;
 *   <li>{@code MISSING_IN_BANK} — present in the ledger but not externally.
 * </ul>
 *
 * Deterministic: references are processed in first-seen order (external first, then ledger-only).
 */
public final class ReconMatcher {

    private ReconMatcher() {}

    public static ReconResult match(List<ReconLine> external, List<ReconLine> ledger) {
        Map<String, List<Money>> externalByRef = group(external);
        Map<String, List<Money>> ledgerByRef = group(ledger);

        Set<String> references = new LinkedHashSet<>();
        references.addAll(externalByRef.keySet());
        references.addAll(ledgerByRef.keySet());

        List<ReconItem> items = new ArrayList<>();
        for (String reference : references) {
            List<Money> ext = externalByRef.getOrDefault(reference, List.of());
            List<Money> led = ledgerByRef.getOrDefault(reference, List.of());

            if (ext.size() > 1 || led.size() > 1) {
                items.add(
                        item(
                                reference,
                                ReconClassification.DUPLICATE,
                                ext.isEmpty() ? null : ext.get(0),
                                led.isEmpty() ? null : led.get(0)));
                continue;
            }
            boolean inExternal = !ext.isEmpty();
            boolean inLedger = !led.isEmpty();
            if (inExternal && inLedger) {
                ReconClassification kind =
                        ext.get(0).equals(led.get(0))
                                ? ReconClassification.MATCHED
                                : ReconClassification.AMOUNT_MISMATCH;
                items.add(item(reference, kind, ext.get(0), led.get(0)));
            } else if (inExternal) {
                items.add(item(reference, ReconClassification.MISSING_IN_LEDGER, ext.get(0), null));
            } else {
                items.add(item(reference, ReconClassification.MISSING_IN_BANK, null, led.get(0)));
            }
        }
        return new ReconResult(items);
    }

    private static Map<String, List<Money>> group(List<ReconLine> lines) {
        Map<String, List<Money>> byRef = new LinkedHashMap<>();
        for (ReconLine line : lines) {
            byRef.computeIfAbsent(line.reference(), k -> new ArrayList<>()).add(line.amount());
        }
        return byRef;
    }

    private static ReconItem item(
            String reference, ReconClassification kind, Money external, Money ledger) {
        return new ReconItem(
                reference, kind, Optional.ofNullable(external), Optional.ofNullable(ledger));
    }
}
