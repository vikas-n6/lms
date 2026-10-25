package com.lms.repayment.application;

import com.lms.money.Money;
import com.lms.repayment.application.port.out.PaymentsClient;
import com.lms.repayment.application.port.out.PaymentsClient.PaymentLink;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates a customer payment link via the payments simulator (phase prompt task 3, {@code POST
 * /repayments}). The confirmed funds arrive later as a signed callback.
 */
public class CreatePaymentLinkService {

    private final PaymentsClient payments;
    private final String callbackUrl;

    public CreatePaymentLinkService(PaymentsClient payments, String callbackUrl) {
        this.payments = Objects.requireNonNull(payments);
        this.callbackUrl = Objects.requireNonNull(callbackUrl);
    }

    public PaymentLink create(UUID loanId, Money amount) {
        return payments.createLink(loanId, amount, callbackUrl);
    }
}
