package com.lms.ledger.domain.recon;

import java.util.List;
import java.util.Objects;

/** The reconciliation outcome (phase prompt task 6): the per-reference items and the counts. */
public record ReconResult(List<ReconItem> items) {

    public ReconResult {
        Objects.requireNonNull(items, "items");
        items = List.copyOf(items);
    }

    public int count(ReconClassification classification) {
        return (int) items.stream().filter(i -> i.classification() == classification).count();
    }

    /** True when every line matched (a clean reconciliation day). */
    public boolean isClean() {
        return items.stream().allMatch(i -> i.classification() == ReconClassification.MATCHED);
    }
}
