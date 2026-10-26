package com.lms.simulators.recon;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * Reconciliation feed simulator (phase prompt task 6): holds the "settled" money-movement lines
 * (bank statement entries and rail settlement report entries) and serves them per date. A drift
 * toggle ({@code reconDriftRate} knob) injects exactly one of each discrepancy type so the
 * reconciliation can be seen to detect them: a spurious line (→ MISSING_IN_LEDGER), a dropped line
 * (→ MISSING_IN_BANK), an altered amount (→ AMOUNT_MISMATCH) and a duplicated line (→ DUPLICATE).
 * Lines are seeded to mirror what the ledger recorded; in-memory only, never a system of record.
 */
@Component
public class ReconSimulator {

    private final CopyOnWriteArrayList<Line> lines = new CopyOnWriteArrayList<>();
    private final AtomicBoolean drift = new AtomicBoolean(false);

    public void record(String reference, long amountMinor, String date, String source) {
        lines.add(
                new Line(reference, amountMinor, date, source.toUpperCase(java.util.Locale.ROOT)));
    }

    public void setDrift(boolean enabled) {
        drift.set(enabled);
    }

    public boolean driftEnabled() {
        return drift.get();
    }

    public void reset() {
        lines.clear();
        drift.set(false);
    }

    /** The lines for a date and source, with drift perturbations applied when the knob is on. */
    public List<Line> feed(String date, String source) {
        String src = source.toUpperCase(java.util.Locale.ROOT);
        List<Line> base = new ArrayList<>();
        for (Line line : lines) {
            if (line.date().equals(date) && line.source().equals(src)) {
                base.add(line);
            }
        }
        if (!drift.get() || base.isEmpty()) {
            return base;
        }
        return withDrift(base, src, date);
    }

    private static List<Line> withDrift(List<Line> base, String source, String date) {
        List<Line> out = new ArrayList<>(base);
        // MISSING_IN_LEDGER: a spurious external line the ledger never recorded.
        out.add(new Line("DRIFT-SPURIOUS-" + source, 9_999L, date, source));
        // AMOUNT_MISMATCH: alter the first line's amount by one minor unit.
        Line first = out.get(0);
        out.set(0, new Line(first.reference(), first.amount() + 1, date, source));
        // DUPLICATE: repeat the last original line.
        out.add(base.get(base.size() - 1));
        // MISSING_IN_BANK: drop a line if there is more than one original (the ledger keeps it).
        if (base.size() > 1) {
            out.remove(base.get(base.size() - 1 == 0 ? 0 : base.size() - 2));
        }
        return out;
    }

    /** A statement / settlement line. */
    public record Line(String reference, long amount, String date, String source) {}
}
