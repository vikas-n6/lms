package com.lms.loanaccount.adapters.in.web;

import com.lms.loanaccount.application.LoanQueryService;
import com.lms.loanaccount.application.LoanQueryService.LoanDetail;
import com.lms.loanaccount.application.LoanQueryService.ScheduleLineView;
import com.lms.loanaccount.application.port.out.StateHistoryRepository;
import com.lms.loanaccount.domain.Loan;
import com.lms.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API for loans (phase prompt task 2): list/filter, detail with balances, schedule, state
 * history and the foreclosure quote. Read access is granted to {@code analyst} at the gateway.
 */
@RestController
@RequestMapping("/loans")
public class LoanController {

    private final LoanQueryService query;

    public LoanController(LoanQueryService query) {
        this.query = query;
    }

    @GetMapping
    public List<LoanSummary> list(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String customerId) {
        return query.list(Optional.ofNullable(state), Optional.ofNullable(customerId)).stream()
                .map(LoanController::summary)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoanDetailResponse> detail(@PathVariable UUID id) {
        return query.detail(id)
                .map(LoanController::detailResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/schedule")
    public List<ScheduleLineView> schedule(@PathVariable UUID id) {
        return query.schedule(id);
    }

    @GetMapping("/{id}/foreclosure-quote")
    public ForeclosureQuoteResponse foreclosureQuote(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        LocalDate quoteDate = date != null ? date : LocalDate.now();
        Money quote = query.foreclosureQuote(id, quoteDate);
        return new ForeclosureQuoteResponse(id, quoteDate, quote);
    }

    private static LoanSummary summary(Loan loan) {
        return new LoanSummary(
                loan.id(),
                loan.loanNumber(),
                loan.customerId(),
                loan.state().code(),
                loan.principal(),
                loan.tenorMonths(),
                loan.disbursementDate());
    }

    private static LoanDetailResponse detailResponse(LoanDetail d) {
        return new LoanDetailResponse(summary(d.loan()), d.balances(), d.schedule(), d.history());
    }

    /** List-row projection. */
    public record LoanSummary(
            UUID id,
            String loanNumber,
            String customerId,
            String state,
            Money principal,
            int tenorMonths,
            LocalDate disbursementDate) {}

    /** Full detail projection. */
    public record LoanDetailResponse(
            LoanSummary loan,
            com.lms.loanaccount.domain.LoanBalances balances,
            List<ScheduleLineView> schedule,
            List<StateHistoryRepository.Entry> history) {}

    public record ForeclosureQuoteResponse(UUID loanId, LocalDate asOf, Money amount) {}
}
