package com.lms.loanaccount.adapters.in.web;

import com.lms.loanaccount.application.WriteOffService;
import com.lms.loanaccount.domain.WriteOffRequest;
import com.lms.security.context.RequestContextHolder;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two-person write-off endpoints (phase prompt task 2). Admin A raises a request at {@code POST
 * /loans/{id}/write-off-requests}; a <em>different</em> admin B approves at {@code POST
 * /write-off-requests/{id}/approve}. The acting user is the authenticated JWT subject (from the
 * request context), with an {@code X-Acting-User} header fallback for local/dev calls; the
 * same-user and DPD guards live in the domain and {@link WriteOffService}.
 */
@RestController
public class WriteOffController {

    private final WriteOffService service;

    public WriteOffController(WriteOffService service) {
        this.service = Objects.requireNonNull(service);
    }

    @PostMapping("/loans/{loanId}/write-off-requests")
    public WriteOffRequest request(
            @PathVariable UUID loanId,
            @RequestBody RaiseRequest body,
            @RequestHeader(value = "X-Acting-User", required = false) String actingUserHeader) {
        return service.request(loanId, body.reason(), actor(actingUserHeader));
    }

    @GetMapping("/write-off-requests")
    public List<WriteOffRequest> list(@RequestParam(required = false) String status) {
        return service.listAll(Optional.ofNullable(status));
    }

    @GetMapping("/loans/{loanId}/write-off-requests")
    public List<WriteOffRequest> listForLoan(@PathVariable UUID loanId) {
        return service.listForLoan(loanId);
    }

    @PostMapping("/write-off-requests/{requestId}/approve")
    public WriteOffRequest approve(
            @PathVariable UUID requestId,
            @RequestHeader(value = "X-Acting-User", required = false) String actingUserHeader) {
        return service.approve(requestId, actor(actingUserHeader));
    }

    @PostMapping("/write-off-requests/{requestId}/reject")
    public WriteOffRequest reject(
            @PathVariable UUID requestId,
            @RequestBody(required = false) RejectRequest body,
            @RequestHeader(value = "X-Acting-User", required = false) String actingUserHeader) {
        String note = body == null ? null : body.note();
        return service.reject(requestId, actor(actingUserHeader), note);
    }

    private static String actor(String headerFallback) {
        String fromContext =
                RequestContextHolder.currentIfBound()
                        .map(com.lms.security.context.RequestContext::userId)
                        .orElse(null);
        String actor = fromContext != null ? fromContext : headerFallback;
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("the acting admin user is required");
        }
        return actor;
    }

    /** Request body to raise a write-off request. */
    public record RaiseRequest(@NotBlank String reason) {}

    /** Optional request body carrying a rejection note. */
    public record RejectRequest(String note) {}
}
