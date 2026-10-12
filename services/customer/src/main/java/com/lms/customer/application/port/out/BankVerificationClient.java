package com.lms.customer.application.port.out;

/** Outbound port to the bank penny-drop simulator. */
public interface BankVerificationClient {

    PennyDrop pennyDrop(String accountNumber, String ifsc);

    /**
     * @param verified true when the penny-drop succeeded
     * @param maskedAccountNumber masked account number returned by the bank (non-PII)
     */
    record PennyDrop(boolean verified, String maskedAccountNumber) {}
}
