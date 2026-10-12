package com.lms.customer.adapters.in.web;

import com.lms.customer.application.ConsentService;
import com.lms.customer.domain.ConsentType;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Append-only consent capture + read (phase prompt S3). */
@RestController
@RequestMapping("/customers/{id}/consents")
public class ConsentController {

    private final ConsentService consents;

    public ConsentController(ConsentService consents) {
        this.consents = consents;
    }

    @PostMapping
    public void grant(@PathVariable String id, @RequestBody GrantConsentRequest request) {
        consents.grant(id, request.type());
    }

    @GetMapping
    public List<String> list(@PathVariable String id) {
        return consents.ledger(id).entries().stream().map(c -> c.type().name()).toList();
    }

    public record GrantConsentRequest(@NotNull ConsentType type) {}
}
