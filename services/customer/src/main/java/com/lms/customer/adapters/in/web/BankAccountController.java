package com.lms.customer.adapters.in.web;

import com.lms.customer.application.BankLinkingService;
import com.lms.customer.domain.BankAccount;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bank account linking with a penny-drop check (phase prompt S3). */
@RestController
@RequestMapping("/customers")
public class BankAccountController {

    private final BankLinkingService linking;

    public BankAccountController(BankLinkingService linking) {
        this.linking = linking;
    }

    @PostMapping("/{id}/bank-accounts")
    public BankAccountResponse link(
            @PathVariable String id, @RequestBody LinkBankAccountRequest request) {
        BankAccount account = linking.link(id, request.accountNumber(), request.ifsc());
        return new BankAccountResponse(
                account.id().toString(),
                account.maskedNumber(),
                account.ifsc(),
                account.pennyDropVerified());
    }

    public record LinkBankAccountRequest(@NotBlank String accountNumber, @NotBlank String ifsc) {}

    public record BankAccountResponse(
            String id, String maskedNumber, String ifsc, boolean pennyDropVerified) {}
}
