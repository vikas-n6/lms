package com.lms.notification.adapters.in.web;

import com.lms.notification.application.port.out.NotificationLogRepository;
import com.lms.notification.application.port.out.NotificationLogRepository.Row;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Notification log admin view (phase prompt task 5): list with an optional status filter. */
@RestController
@RequestMapping("/notifications")
public class NotificationLogController {

    private final NotificationLogRepository log;

    public NotificationLogController(NotificationLogRepository log) {
        this.log = Objects.requireNonNull(log);
    }

    @GetMapping("/log")
    public List<Row> list(@RequestParam(required = false) String status) {
        return log.list(Optional.ofNullable(status));
    }
}
