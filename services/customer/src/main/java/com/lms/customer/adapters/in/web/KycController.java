package com.lms.customer.adapters.in.web;

import com.lms.customer.application.KycService;
import com.lms.customer.domain.KycStatus;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** KYC submission + the simulator's async resolution callback (phase prompt S3). */
@RestController
@RequestMapping("/customers")
public class KycController {

    private final KycService kyc;

    public KycController(KycService kyc) {
        this.kyc = kyc;
    }

    @PostMapping("/{id}/kyc")
    public KycResponse submit(@PathVariable String id, @RequestBody SubmitKycRequest request) {
        return new KycResponse(kyc.submit(id, request.idNumber()));
    }

    @PostMapping("/kyc/callback")
    public void callback(@RequestBody KycCallbackRequest request) {
        kyc.onCallback(request.subjectId(), KycStatus.valueOf(request.status()));
    }

    public record SubmitKycRequest(@NotBlank String idNumber) {}

    public record KycCallbackRequest(@NotBlank String subjectId, @NotBlank String status) {}

    public record KycResponse(KycStatus status) {}
}
