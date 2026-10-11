package com.lms.gateway.status;

import com.lms.gateway.status.StatusReport.ServiceStatus;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Aggregates each configured service's health into one report for the Admin UI status page. */
@RestController
public class StatusController {

    private final ServiceHealthProbe probe;
    private final GatewayServicesProperties properties;

    public StatusController(ServiceHealthProbe probe, GatewayServicesProperties properties) {
        this.probe = probe;
        this.properties = properties;
    }

    @GetMapping("/api/status")
    public StatusReport status() {
        List<ServiceStatus> statuses =
                properties.services().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> new ServiceStatus(e.getKey(), probe.probe(e.getValue())))
                        .toList();
        return new StatusReport(statuses);
    }
}
