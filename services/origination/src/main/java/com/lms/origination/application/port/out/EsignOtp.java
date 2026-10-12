package com.lms.origination.application.port.out;

/**
 * OTP e-sign for offer acceptance (phase prompt S4). The adapter issues a code via the SMS
 * simulator and stores it in Redis with a TTL; verification is single-use. Keyed by application id.
 */
public interface EsignOtp {

    void issue(String applicationId, String phone);

    boolean verify(String applicationId, String code);
}
