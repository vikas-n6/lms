package com.lms.simulators.behavior;

/** Thrown when a simulator's injected failure rate makes a call fail (mapped to HTTP 503). */
public class SimulatorUnavailableException extends RuntimeException {
    public SimulatorUnavailableException(String simulator) {
        super(simulator + " simulator is unavailable");
    }
}
