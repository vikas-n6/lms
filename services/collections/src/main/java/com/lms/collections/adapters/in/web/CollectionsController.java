package com.lms.collections.adapters.in.web;

import com.lms.collections.application.CaseQueryService;
import com.lms.collections.application.CaseQueryService.CaseDetail;
import com.lms.collections.application.CollectionCaseNotFoundException;
import com.lms.collections.application.CollectionsService;
import com.lms.collections.domain.CollectionCase;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Collections APIs (phase prompt task 4): case list with filters, case detail with the action
 * timeline, assign, record a promise to pay, escalate and manually resolve. Admin actions route
 * through {@link CollectionsService} so the durable workflow stays the single source of case
 * progress.
 */
@RestController
@RequestMapping("/collections")
public class CollectionsController {

    private final CaseQueryService query;
    private final CollectionsService service;

    public CollectionsController(CaseQueryService query, CollectionsService service) {
        this.query = Objects.requireNonNull(query);
        this.service = Objects.requireNonNull(service);
    }

    @GetMapping("/cases")
    public List<CollectionCase> list(
            @RequestParam(required = false) String bucket,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String agent) {
        return query.list(
                Optional.ofNullable(bucket),
                Optional.ofNullable(status),
                Optional.ofNullable(agent));
    }

    @GetMapping("/cases/{caseId}")
    public CaseDetail detail(@PathVariable UUID caseId) {
        return query.detail(caseId).orElseThrow(() -> new CollectionCaseNotFoundException(caseId));
    }

    @PostMapping("/cases/{caseId}/assign")
    public void assign(@PathVariable UUID caseId, @RequestBody AssignRequest body) {
        service.assignAgent(caseId, body.agent());
    }

    @PostMapping("/cases/{caseId}/promise")
    public void promise(@PathVariable UUID caseId, @RequestBody PromiseRequest body) {
        service.recordPromise(caseId, body.date(), body.amountMinor(), body.currency());
    }

    @PostMapping("/cases/{caseId}/escalate")
    public void escalate(
            @PathVariable UUID caseId, @RequestBody(required = false) ReasonRequest body) {
        service.escalate(caseId, body == null ? "manual escalation" : body.reason());
    }

    @PostMapping("/cases/{caseId}/resolve")
    public void resolve(
            @PathVariable UUID caseId, @RequestBody(required = false) ReasonRequest body) {
        service.resolve(caseId, body == null ? "manual resolution" : body.reason());
    }

    public record AssignRequest(@NotBlank String agent) {}

    public record PromiseRequest(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            long amountMinor,
            String currency) {}

    public record ReasonRequest(String reason) {}
}
