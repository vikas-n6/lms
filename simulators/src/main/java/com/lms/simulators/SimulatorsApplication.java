package com.lms.simulators;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Owned simulators for every external party (bureau, KYC, bank, SMS) with a control API for
 * latency, failure, timeout, duplicate callbacks and seed (docs/architecture.md "simulators").
 */
@SpringBootApplication
public class SimulatorsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimulatorsApplication.class, args);
    }
}
