package com.lms.repayment.adapters.in.web;

import com.lms.repayment.application.RegisterMandateService;
import com.lms.repayment.application.port.out.MandateClient;
import com.lms.repayment.application.port.out.MandateRepository;
import com.lms.repayment.domain.Mandate;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mandate lifecycle API (phase prompt task 3): register, status, cancel. */
@RestController
@RequestMapping("/mandates")
public class MandateController {

    private final RegisterMandateService registerService;
    private final MandateRepository mandates;
    private final MandateClient client;

    public MandateController(
            RegisterMandateService registerService,
            MandateRepository mandates,
            MandateClient client) {
        this.registerService = registerService;
        this.mandates = mandates;
        this.client = client;
    }

    @PostMapping
    public MandateView register(@RequestBody RegisterMandate request) {
        return view(
                registerService.register(UUID.fromString(request.loanId()), request.customerId()));
    }

    @GetMapping("/{loanId}")
    public ResponseEntity<MandateView> status(@PathVariable UUID loanId) {
        return mandates.findActiveByLoanId(loanId)
                .map(m -> ResponseEntity.ok(view(m)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MandateView> cancel(@PathVariable UUID id) {
        return mandates.findById(id)
                .map(
                        m -> {
                            client.cancel(m.providerMandateId());
                            Mandate cancelled = m.cancel(m.updatedAt());
                            mandates.save(cancelled);
                            return ResponseEntity.ok(view(cancelled));
                        })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static MandateView view(Mandate m) {
        return new MandateView(
                m.id().toString(), m.loanId().toString(), m.customerId(), m.status().name());
    }

    public record RegisterMandate(@NotBlank String loanId, @NotBlank String customerId) {}

    public record MandateView(String id, String loanId, String customerId, String status) {}
}
