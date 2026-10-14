package com.lms.ledger.adapters.in.web;

import com.lms.ledger.application.LedgerQueryService;
import com.lms.ledger.application.ReverseEntryService;
import com.lms.ledger.domain.AccountCode;
import com.lms.ledger.domain.JournalEntry;
import com.lms.ledger.domain.TrialBalance;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ledger read API and the admin reverse endpoint (phase prompt task 3): entries with filters, entry
 * detail with postings, account balance as of a date, trial balance with a zero-check, per-loan
 * sub-ledger, and {@code POST /journal-entries/{id}/reverse}. Read access is granted to {@code
 * analyst} at the gateway; reversal is admin-only.
 */
@RestController
public class LedgerController {

    private final LedgerQueryService query;
    private final ReverseEntryService reverseService;

    public LedgerController(LedgerQueryService query, ReverseEntryService reverseService) {
        this.query = query;
        this.reverseService = reverseService;
    }

    @GetMapping("/journal-entries")
    public List<JournalEntry> entries(
            @RequestParam(required = false) String loanId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date,
            @RequestParam(required = false) String account) {
        return query.entries(
                Optional.ofNullable(loanId),
                Optional.ofNullable(date),
                Optional.ofNullable(account));
    }

    @GetMapping("/journal-entries/{id}")
    public ResponseEntity<JournalEntry> entry(@PathVariable UUID id) {
        return query.entry(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/journal-entries/{id}/reverse")
    public JournalEntry reverse(@PathVariable UUID id, @RequestBody ReverseRequest request) {
        return reverseService.reverse(id, request.reason());
    }

    @GetMapping("/ledger/loans/{loanId}")
    public List<JournalEntry> subLedger(@PathVariable String loanId) {
        return query.subLedger(loanId);
    }

    @GetMapping("/ledger/accounts/{code}/balance")
    public Map<String, Object> accountBalance(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        LocalDate asOf = date != null ? date : LocalDate.now();
        long balance = query.accountBalanceAsOf(AccountCode.ofCode(code), asOf);
        return Map.of("account", code, "asOf", asOf.toString(), "normalBalanceMinorUnits", balance);
    }

    @GetMapping("/ledger/trial-balance")
    public TrialBalanceResponse trialBalance() {
        TrialBalance tb = query.trialBalance();
        return new TrialBalanceResponse(
                tb.totalDebitMinorUnits(),
                tb.totalCreditMinorUnits(),
                tb.diffMinorUnits(),
                tb.balanced(),
                tb.normalBalances());
    }

    public record ReverseRequest(@NotBlank String reason) {}

    public record TrialBalanceResponse(
            long totalDebitMinorUnits,
            long totalCreditMinorUnits,
            long diffMinorUnits,
            boolean balanced,
            Map<AccountCode, Long> normalBalances) {}
}
