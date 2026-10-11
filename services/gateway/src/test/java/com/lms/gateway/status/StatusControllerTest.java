package com.lms.gateway.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatusControllerTest {

    @Test
    void aggregatesEachConfiguredServiceSortedByName() {
        Map<String, String> services = new LinkedHashMap<>();
        services.put("customer", "http://customer:8101");
        services.put("ledger", "http://ledger:8108");
        GatewayServicesProperties properties = new GatewayServicesProperties(services);

        ServiceHealthProbe probe = baseUrl -> baseUrl.contains("ledger") ? "DOWN" : "UP";
        StatusReport report = new StatusController(probe, properties).status();

        assertThat(report.services())
                .containsExactly(
                        new StatusReport.ServiceStatus("customer", "UP"),
                        new StatusReport.ServiceStatus("ledger", "DOWN"));
    }

    @Test
    void emptyWhenNoServicesConfigured() {
        StatusReport report =
                new StatusController(url -> "UP", new GatewayServicesProperties(null)).status();
        assertThat(report.services()).isEmpty();
    }
}
