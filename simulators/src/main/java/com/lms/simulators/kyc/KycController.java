package com.lms.simulators.kyc;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sim/kyc")
public class KycController {

    private final KycSimulator simulator;

    public KycController(KycSimulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping("/verify")
    public VerifyResponse verify(@RequestBody VerifyRequest request) {
        KycStatus status = simulator.verify(request.subjectId(), request.callbackUrl());
        return new VerifyResponse(status);
    }

    public record VerifyRequest(@NotBlank String subjectId, String callbackUrl) {}

    public record VerifyResponse(KycStatus status) {}
}
