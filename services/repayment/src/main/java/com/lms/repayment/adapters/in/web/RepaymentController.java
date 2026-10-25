package com.lms.repayment.adapters.in.web;

import com.lms.money.Money;
import com.lms.repayment.application.CreatePaymentLinkService;
import com.lms.repayment.application.RecordRepaymentService;
import com.lms.repayment.application.port.out.PaymentsClient.PaymentLink;
import com.lms.repayment.domain.RepaymentChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.Currency;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer-initiated and admin-manual repayments (phase prompt task 3). {@code POST /repayments}
 * returns a payment link from the simulator; {@code POST /repayments/manual} records an admin
 * receipt with a bank reference (which may be a foreclosure, carrying the quote id).
 */
@RestController
@RequestMapping("/repayments")
public class RepaymentController {

    private final CreatePaymentLinkService paymentLinks;
    private final RecordRepaymentService recordService;

    public RepaymentController(
            CreatePaymentLinkService paymentLinks, RecordRepaymentService recordService) {
        this.paymentLinks = paymentLinks;
        this.recordService = recordService;
    }

    @PostMapping
    public PaymentLink create(@RequestBody CreateRepayment request) {
        return paymentLinks.create(
                UUID.fromString(request.loanId()),
                Money.of(request.amountMinor(), Currency.getInstance(request.currency())));
    }

    @PostMapping("/manual")
    public ManualReceiptResponse manual(@RequestBody ManualReceipt request) {
        Money amount = Money.of(request.amountMinor(), Currency.getInstance(request.currency()));
        return recordService
                .record(
                        UUID.fromString(request.loanId()),
                        amount,
                        RepaymentChannel.MANUAL,
                        request.purpose(),
                        request.foreclosureQuoteId(),
                        request.bankReference())
                .map(r -> new ManualReceiptResponse(r.id().toString(), "RECORDED"))
                .orElse(new ManualReceiptResponse(null, "DUPLICATE"));
    }

    public record CreateRepayment(
            @NotBlank String loanId,
            @Positive long amountMinor,
            @NotBlank String currency,
            String purpose,
            String foreclosureQuoteId) {}

    public record ManualReceipt(
            @NotBlank String loanId,
            @Positive long amountMinor,
            @NotBlank String currency,
            @NotBlank String bankReference,
            String purpose,
            String foreclosureQuoteId) {}

    public record ManualReceiptResponse(String repaymentId, String status) {}
}
