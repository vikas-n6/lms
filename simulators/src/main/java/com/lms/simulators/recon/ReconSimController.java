package com.lms.simulators.recon;

import com.lms.simulators.recon.ReconSimulator.Line;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP surface of the reconciliation simulator (phase prompt task 6): the bank statement and rail
 * settlement report per date, a seed endpoint to mirror the ledger's money movements, and the drift
 * toggle. The ledger's daily reconciliation reads the two report endpoints.
 */
@RestController
@RequestMapping("/sim")
public class ReconSimController {

    private final ReconSimulator simulator;

    public ReconSimController(ReconSimulator simulator) {
        this.simulator = simulator;
    }

    @GetMapping("/bank/statement")
    public List<ReportLine> bankStatement(@RequestParam String date) {
        return toReport(simulator.feed(date, "BANK"));
    }

    @GetMapping("/rail/settlement-report")
    public List<ReportLine> settlementReport(@RequestParam String date) {
        return toReport(simulator.feed(date, "RAIL"));
    }

    @PostMapping("/recon/lines")
    public void record(@RequestBody SeedRequest request) {
        simulator.record(
                request.reference(), request.amountMinor(), request.date(), request.source());
    }

    @PostMapping("/recon/drift")
    public void drift(@RequestParam boolean enabled) {
        simulator.setDrift(enabled);
    }

    @PostMapping("/recon/reset")
    public void reset() {
        simulator.reset();
    }

    private static List<ReportLine> toReport(List<Line> lines) {
        return lines.stream().map(l -> new ReportLine(l.reference(), l.amount())).toList();
    }

    public record SeedRequest(String reference, long amountMinor, String date, String source) {}

    public record ReportLine(String reference, long amountMinor) {}
}
