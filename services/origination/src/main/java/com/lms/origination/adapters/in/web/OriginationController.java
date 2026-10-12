package com.lms.origination.adapters.in.web;

import com.lms.money.Money;
import com.lms.origination.application.CreateDraftCommand;
import com.lms.origination.application.OriginationService;
import com.lms.origination.domain.Application;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for applications (phase prompt S4). Routed from the gateway at /api/applications/**.
 */
@RestController
@RequestMapping("/applications")
public class OriginationController {

    private final OriginationService service;
    private final Currency currency;

    public OriginationController(
            OriginationService service, @Value("${lms.currency.default:INR}") String currencyCode) {
        this.service = service;
        this.currency = Currency.getInstance(currencyCode);
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(
            @Valid @RequestBody CreateApplicationRequest r) {
        Application app =
                service.createDraft(
                        new CreateDraftCommand(
                                r.customerId(),
                                r.productId(),
                                Money.of(r.requestedAmountMinor(), currency),
                                r.phone(),
                                r.dateOfBirth(),
                                Money.of(r.declaredMonthlyIncomeMinor(), currency),
                                r.employmentType(),
                                r.dataProcessingConsent(),
                                r.creditPullConsent(),
                                r.loanAgreementConsent()));
        ApplicationResponse body = ApplicationResponse.from(app);
        return ResponseEntity.created(URI.create("/applications/" + body.id())).body(body);
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable String id) {
        return ApplicationResponse.from(service.get(id));
    }

    @GetMapping
    public List<ApplicationResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.list(status, limit, offset).stream().map(ApplicationResponse::from).toList();
    }

    @PostMapping("/{id}/submit")
    public ApplicationResponse submit(@PathVariable String id) {
        return ApplicationResponse.from(service.submit(id));
    }

    @PostMapping("/{id}/withdraw")
    public ApplicationResponse withdraw(@PathVariable String id) {
        return ApplicationResponse.from(service.withdraw(id));
    }

    @PostMapping("/{id}/esign")
    public void requestEsign(@PathVariable String id) {
        service.requestEsign(id);
    }

    @PostMapping("/{id}/accept")
    public ApplicationResponse accept(
            @PathVariable String id, @Valid @RequestBody AcceptRequest r) {
        return ApplicationResponse.from(service.accept(id, r.tenorMonths(), r.otpCode()));
    }

    /** Create draft (back-office wizard). Amounts are minor units in the configured currency. */
    public record CreateApplicationRequest(
            @NotBlank String customerId,
            @NotBlank String productId,
            @Positive long requestedAmountMinor,
            @NotBlank String phone,
            @NotNull LocalDate dateOfBirth,
            @Positive long declaredMonthlyIncomeMinor,
            @NotBlank String employmentType,
            boolean dataProcessingConsent,
            boolean creditPullConsent,
            boolean loanAgreementConsent) {}

    public record AcceptRequest(@Positive int tenorMonths, @NotBlank String otpCode) {}
}
