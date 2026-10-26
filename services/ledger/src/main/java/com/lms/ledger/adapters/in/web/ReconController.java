package com.lms.ledger.adapters.in.web;

import com.lms.ledger.application.BankRailReconciliationService;
import com.lms.ledger.application.ReverseEntryService;
import com.lms.ledger.application.port.out.ReconRunRepository;
import com.lms.ledger.application.port.out.ReconRunRepository.Run;
import com.lms.ledger.application.port.out.ReconRunRepository.StoredItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reconciliation APIs (phase prompt task 6): trigger a run for a date, list runs and their items,
 * resolve an item with a note, or raise an adjusting entry through the ledger's reversal mechanism.
 */
@RestController
public class ReconController {

    private final BankRailReconciliationService reconciliation;
    private final ReconRunRepository runs;
    private final ReverseEntryService reverseEntryService;

    public ReconController(
            BankRailReconciliationService reconciliation,
            ReconRunRepository runs,
            ReverseEntryService reverseEntryService) {
        this.reconciliation = Objects.requireNonNull(reconciliation);
        this.runs = Objects.requireNonNull(runs);
        this.reverseEntryService = Objects.requireNonNull(reverseEntryService);
    }

    @PostMapping("/admin/recon/run")
    public Map<String, Object> run(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID runId = reconciliation.reconcile(date);
        return Map.of("runId", runId.toString(), "date", date.toString());
    }

    @GetMapping("/recon/runs")
    public List<Run> listRuns() {
        return runs.listRuns();
    }

    @GetMapping("/recon/runs/{runId}/items")
    public List<StoredItem> items(@PathVariable UUID runId) {
        return runs.itemsForRun(runId);
    }

    @PostMapping("/recon/items/{itemId}/resolve")
    public void resolve(@PathVariable UUID itemId, @RequestBody ResolveRequest body) {
        reconciliation.resolve(itemId, body.note());
    }

    /** Raise an adjusting entry by reversing an existing journal entry (the reversal mechanism). */
    @PostMapping("/recon/items/{itemId}/adjust")
    public void adjust(@PathVariable UUID itemId, @RequestBody AdjustRequest body) {
        reverseEntryService.reverse(UUID.fromString(body.journalEntryId()), body.reason());
        reconciliation.resolve(itemId, "adjusted via reversal of " + body.journalEntryId());
    }

    public record ResolveRequest(String note) {}

    public record AdjustRequest(String journalEntryId, String reason) {}
}
