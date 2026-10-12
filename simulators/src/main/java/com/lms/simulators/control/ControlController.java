package com.lms.simulators.control;

import com.lms.simulators.config.SimConfigRegistry;
import com.lms.simulators.config.SimulatorConfig;
import com.lms.simulators.config.SimulatorPreset;
import com.lms.simulators.sms.SmsStore;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Control API for the simulator estate (phase prompt S2): per-simulator config, presets, reset. */
@RestController
@RequestMapping("/sim")
public class ControlController {

    private final SimConfigRegistry registry;
    private final SmsStore smsStore;

    public ControlController(SimConfigRegistry registry, SmsStore smsStore) {
        this.registry = registry;
        this.smsStore = smsStore;
    }

    @GetMapping("/config")
    public Map<String, SimulatorConfig> all() {
        return registry.all();
    }

    @GetMapping("/config/{simulator}")
    public SimulatorConfig get(@PathVariable String simulator) {
        return registry.get(simulator);
    }

    @PutMapping("/config/{simulator}")
    public SimulatorConfig put(
            @PathVariable String simulator, @RequestBody SimulatorConfig config) {
        return registry.put(simulator, config);
    }

    @PostMapping("/config/{simulator}/preset/{preset}")
    public SimulatorConfig applyPreset(
            @PathVariable String simulator,
            @PathVariable String preset,
            @RequestParam(name = "seed", defaultValue = "1") long seed) {
        return registry.applyPreset(simulator, SimulatorPreset.fromString(preset), seed);
    }

    @PostMapping("/preset/{preset}")
    public Map<String, SimulatorConfig> applyPresetToAll(
            @PathVariable String preset,
            @RequestParam(name = "seed", defaultValue = "1") long seed) {
        registry.applyPresetToAll(SimulatorPreset.fromString(preset), seed);
        return registry.all();
    }

    @PostMapping("/reset")
    public Map<String, SimulatorConfig> reset() {
        registry.resetAll();
        smsStore.clear();
        return registry.all();
    }
}
