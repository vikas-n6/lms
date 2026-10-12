package com.lms.simulators.sms;

import com.lms.simulators.behavior.SimBehavior;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sim/sms")
public class SmsController {

    private final SmsStore store;
    private final SimBehavior behavior;

    public SmsController(SmsStore store, SimBehavior behavior) {
        this.store = store;
        this.behavior = behavior;
    }

    @PostMapping("/send")
    public SmsMessage send(@RequestBody SendRequest request) {
        behavior.gate("sms");
        return store.send(request.to(), request.body());
    }

    @GetMapping("/outbox")
    public List<SmsMessage> outbox(@RequestParam(name = "to", required = false) String to) {
        return to == null ? store.outbox() : store.outboxFor(to);
    }

    public record SendRequest(@NotBlank String to, @NotBlank String body) {}
}
