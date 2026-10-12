package com.lms.customer.adapters.in.web;

import com.lms.customer.application.OtpService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Phone-verification OTP issue/verify (phase prompt S3). The code travels only via the SMS sim. */
@RestController
@RequestMapping("/customers/{id}/otp")
public class OtpController {

    private final OtpService otp;

    public OtpController(OtpService otp) {
        this.otp = otp;
    }

    @PostMapping("/send")
    public void send(@PathVariable String id, @RequestBody SendOtpRequest request) {
        otp.issue(key(id), request.phone());
    }

    @PostMapping("/verify")
    public VerifyResponse verify(@PathVariable String id, @RequestBody VerifyOtpRequest request) {
        return new VerifyResponse(otp.verify(key(id), request.code()));
    }

    private static String key(String customerId) {
        return "lms:customer:otp:" + customerId;
    }

    public record SendOtpRequest(@NotBlank String phone) {}

    public record VerifyOtpRequest(@NotBlank String code) {}

    public record VerifyResponse(boolean verified) {}
}
