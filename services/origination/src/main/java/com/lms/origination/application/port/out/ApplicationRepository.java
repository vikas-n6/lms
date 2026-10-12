package com.lms.origination.application.port.out;

import com.lms.origination.domain.Application;
import com.lms.origination.domain.ApplicationId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence port for applications (JDBC adapter writes applications + application_events). */
public interface ApplicationRepository {

    void save(Application application);

    Optional<Application> findById(ApplicationId id);

    /** List applications, optionally filtered by status code, newest first, paged. */
    List<Application> list(String statusCodeOrNull, int limit, int offset);

    /**
     * Offered applications whose offer has expired at or before {@code now} (for the expiry job).
     */
    List<Application> findExpired(Instant now);
}
