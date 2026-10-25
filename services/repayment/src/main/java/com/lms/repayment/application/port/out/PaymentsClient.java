package com.lms.repayment.application.port.out;

import com.lms.money.Money;
import java.util.UUID;

/** Outbound port to the payments-link simulator (implemented by an HTTP adapter). */
public interface PaymentsClient {

    PaymentLink createLink(UUID loanId, Money amount, String callbackUrl);

    record PaymentLink(String linkId, String url) {}
}
