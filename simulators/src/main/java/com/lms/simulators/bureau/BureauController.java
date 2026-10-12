package com.lms.simulators.bureau;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sim/bureau")
public class BureauController {

    private final BureauSimulator simulator;

    public BureauController(BureauSimulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping("/pull")
    public BureauReport pull(@RequestBody PullRequest request) {
        return simulator.pull(request.subjectId(), request.phone());
    }

    /**
     * @param subjectId stable id the score is derived from; @param phone drives persona overrides
     */
    public record PullRequest(@NotBlank String subjectId, String phone) {}
}
