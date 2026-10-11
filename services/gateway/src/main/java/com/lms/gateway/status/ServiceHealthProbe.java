package com.lms.gateway.status;

/** Probes one service's health endpoint, returning {@code UP}, {@code DOWN} or {@code UNKNOWN}. */
public interface ServiceHealthProbe {

    String probe(String baseUrl);
}
