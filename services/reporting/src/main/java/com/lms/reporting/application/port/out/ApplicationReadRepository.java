package com.lms.reporting.application.port.out;

import com.lms.reporting.domain.ApplicationFunnelRow;
import java.util.List;

/**
 * Read-model store for the application funnel. Stage flags are OR-merged across events (a later
 * event only ever sets more flags true), so projection is order-independent and idempotent.
 */
public interface ApplicationReadRepository {

    /** Merge the given stage flags into the row (true wins), creating it if absent. */
    void merge(String applicationId, String customerId, ApplicationFunnelRow flags);

    List<ApplicationFunnelRow> findAll();
}
