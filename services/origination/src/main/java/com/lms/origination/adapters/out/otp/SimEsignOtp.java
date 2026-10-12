package com.lms.origination.adapters.out.otp;

import com.lms.origination.application.port.out.EsignOtp;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * OTP e-sign via the SMS simulator (phase prompt S4). Dev/default store is in-memory and
 * single-use; the production store is Redis with a TTL (Docker session), behind the same {@link
 * EsignOtp} port.
 */
@Component
public class SimEsignOtp implements EsignOtp {

    private final RestClient restClient;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, String> codes = new ConcurrentHashMap<>();

    public SimEsignOtp(@Value("${lms.sim.base-url:http://localhost:8190}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public void issue(String applicationId, String phone) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        codes.put(applicationId, code);
        restClient
                .post()
                .uri("/sim/sms/send")
                .body(Map.of("to", phone, "body", "Your LMS e-sign code is " + code))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public boolean verify(String applicationId, String code) {
        return code != null
                && code.equals(codes.get(applicationId))
                && codes.remove(applicationId) != null;
    }
}
