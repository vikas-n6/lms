package com.lms.simulators.bureau;

import com.lms.simulators.behavior.SimBehavior;
import org.springframework.stereotype.Component;

/**
 * Credit-bureau simulator (phase prompt S2). The score is derived deterministically from the hash
 * of the subject id, with persona overrides by phone prefix so test scenarios are repeatable:
 *
 * <pre>
 *   9000 approve-high   9001 approve-mid   9002 reject-low-score
 *   9003 refer-band     9004 bureau-delinquent
 * </pre>
 */
@Component
public class BureauSimulator {

    private static final int MIN_SCORE = 300;
    private static final int MAX_SCORE = 900;

    private final SimBehavior behavior;

    public BureauSimulator(SimBehavior behavior) {
        this.behavior = behavior;
    }

    /** Apply latency/failure, then return the (deterministic) report. */
    public BureauReport pull(String subjectId, String phone) {
        behavior.gate("bureau");
        return report(subjectId, phone);
    }

    /** The pure, deterministic report (no latency/failure); used directly in tests. */
    public BureauReport report(String subjectId, String phone) {
        Persona persona = Persona.forPhone(phone);
        if (persona != null) {
            return persona.report;
        }
        int hash = Math.floorMod(String.valueOf(subjectId).hashCode(), MAX_SCORE - MIN_SCORE + 1);
        int score = MIN_SCORE + hash;
        int activeAccounts = Math.floorMod(hash, 4);
        boolean overdue = Math.floorMod(hash, 7) == 0;
        return new BureauReport(score, activeAccounts, overdue);
    }

    private enum Persona {
        APPROVE_HIGH("9000", new BureauReport(800, 1, false)),
        APPROVE_MID("9001", new BureauReport(710, 1, false)),
        REJECT_LOW("9002", new BureauReport(560, 0, false)),
        REFER_BAND("9003", new BureauReport(640, 0, false)),
        DELINQUENT("9004", new BureauReport(720, 2, true));

        private final String prefix;
        private final BureauReport report;

        Persona(String prefix, BureauReport report) {
            this.prefix = prefix;
            this.report = report;
        }

        static Persona forPhone(String phone) {
            if (phone == null || phone.length() < 4) {
                return null;
            }
            String prefix = phone.substring(0, 4);
            for (Persona persona : values()) {
                if (persona.prefix.equals(prefix)) {
                    return persona;
                }
            }
            return null;
        }
    }
}
