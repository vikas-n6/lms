package com.lms.gateway.status;

import java.util.List;

/** Aggregated health of every downstream service, returned by {@code GET /api/status}. */
public record StatusReport(List<ServiceStatus> services) {

    public record ServiceStatus(String name, String status) {}
}
