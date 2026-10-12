package com.lms.customer.adapters.out.sms;

import com.lms.customer.application.port.out.SmsSender;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Sends SMS through the SMS simulator (phase prompt S3). */
@Component
public class SimSmsSender implements SmsSender {

    private final RestClient restClient;

    public SimSmsSender(@Value("${lms.sim.base-url:http://localhost:8190}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public void send(String phone, String message) {
        restClient
                .post()
                .uri("/sim/sms/send")
                .body(Map.of("to", phone, "body", message))
                .retrieve()
                .toBodilessEntity();
    }
}
