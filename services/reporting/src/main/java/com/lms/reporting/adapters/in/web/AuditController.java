package com.lms.reporting.adapters.in.web;

import com.lms.reporting.application.ReportingQueryService;
import com.lms.reporting.domain.AuditLogRecord;
import com.lms.reporting.domain.AuditQuery;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Filtered, bounded audit-log query (ADR 0014). Routed behind the gateway at {@code /api/audit}.
 */
@RestController
@RequestMapping("/audit")
public class AuditController {

    private final ReportingQueryService queries;

    public AuditController(ReportingQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public List<AuditLogRecord> query(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @RequestParam(defaultValue = "100") int limit) {
        return queries.audit(new AuditQuery(actor, action, targetType, targetId, from, to, limit));
    }
}
