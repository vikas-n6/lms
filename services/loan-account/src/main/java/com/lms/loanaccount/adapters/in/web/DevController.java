package com.lms.loanaccount.adapters.in.web;

import com.lms.config.BusinessCalendar;
import com.lms.events.Uuid7;
import com.lms.loanaccount.application.ActivateLoanService;
import com.lms.loanaccount.application.AllocateRepaymentService;
import com.lms.loanaccount.application.LoanNotFoundException;
import com.lms.loanaccount.application.port.out.LoanRepository;
import com.lms.loanaccount.domain.Loan;
import com.lms.money.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEV-only stand-ins for the Phase 3 disbursement/repayment producers (phase prompt task 2), active
 * only under the {@code dev} profile. They drive the same internal handling as the real {@code
 * disbursement.succeeded} / {@code repayment.received} events.
 */
@Profile("dev")
@RestController
@RequestMapping("/admin/loans")
public class DevController {

    private final ActivateLoanService activateService;
    private final AllocateRepaymentService allocateService;
    private final LoanRepository loans;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public DevController(
            ActivateLoanService activateService,
            AllocateRepaymentService allocateService,
            LoanRepository loans,
            BusinessCalendar calendar,
            Clock clock) {
        this.activateService = activateService;
        this.allocateService = allocateService;
        this.loans = Objects.requireNonNull(loans);
        this.calendar = calendar;
        this.clock = clock;
    }

    /** Pretend disbursement succeeded for the loan's exact net on the given date. */
    @PostMapping("/{id}/simulate-disbursement")
    public Map<String, Object> simulateDisbursement(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        Loan loan = loans.findById(id).orElseThrow(() -> new LoanNotFoundException(id));
        LocalDate on = date != null ? date : calendar.today();
        Loan active = activateService.activate(id, loan.netDisbursal(), on, Uuid7.generate(clock));
        return Map.of(
                "loanId", id.toString(),
                "state", active.state().code(),
                "disbursedOn", on.toString());
    }

    /** Inject a repayment to exercise the allocation waterfall before Phase 3 exists. */
    @PostMapping("/{id}/inject-repayment")
    public Map<String, Object> injectRepayment(
            @PathVariable UUID id, @Valid @RequestBody InjectRepaymentRequest request) {
        Loan loan = loans.findById(id).orElseThrow(() -> new LoanNotFoundException(id));
        Currency currency =
                request.currency() != null
                        ? Currency.getInstance(request.currency())
                        : loan.principal().currency();
        String repaymentId =
                request.repaymentId() != null
                        ? request.repaymentId()
                        : "DEV-" + Uuid7.generate(clock);
        allocateService.allocate(
                id, repaymentId, Money.of(request.amountMinor(), currency), Uuid7.generate(clock));
        return Map.of("loanId", id.toString(), "repaymentId", repaymentId);
    }

    public record InjectRepaymentRequest(
            @Positive long amountMinor, String currency, String repaymentId) {}
}
