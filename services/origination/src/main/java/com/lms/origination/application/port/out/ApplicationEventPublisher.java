package com.lms.origination.application.port.out;

import com.lms.origination.application.ApplicantSnapshotData;
import com.lms.origination.domain.Application;
import java.util.List;

/**
 * Publishes the application lifecycle events via the transactional outbox, in the same transaction
 * as the aggregate save (docs/architecture.md): {@code application.submitted} (carrying the
 * self-contained snapshot), {@code .offered}, {@code .accepted}, {@code .rejected}, {@code
 * .expired}.
 */
public interface ApplicationEventPublisher {

    void submitted(Application application, ApplicantSnapshotData snapshot);

    void offered(Application application);

    void accepted(Application application);

    void rejected(Application application, List<String> reasonCodes);

    void expired(Application application);
}
