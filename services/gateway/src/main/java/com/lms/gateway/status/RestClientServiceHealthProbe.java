package com.lms.gateway.status;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Probes {@code <baseUrl>/actuator/health} over HTTP and reads the {@code status} field. */
@Component
public class RestClientServiceHealthProbe implements ServiceHealthProbe {

    private final RestClient restClient = RestClient.create();

    @Override
    public String probe(String baseUrl) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body =
                    restClient.get().uri(baseUrl + "/actuator/health").retrieve().body(Map.class);
            Object status = body == null ? null : body.get("status");
            return status == null ? "UNKNOWN" : status.toString();
        } catch (RuntimeException e) {
            return "DOWN";
        }
    }
}
