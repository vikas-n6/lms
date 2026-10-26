package com.lms.ledger.application.port.out;

import com.lms.ledger.domain.recon.ReconLine;
import java.time.LocalDate;
import java.util.List;

/**
 * The external feeds reconciled against the ledger (phase prompt task 6): the bank statement and
 * the rail settlement report for a date, from the simulators. A {@code reconDriftRate} knob on the
 * simulators injects discrepancies.
 */
public interface ExternalReconFeed {

    List<ReconLine> bankStatement(LocalDate date);

    List<ReconLine> railReport(LocalDate date);
}
