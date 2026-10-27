package com.lms.reporting.application;

import com.lms.contracts.collections.CollectionsCaseClosed;
import com.lms.contracts.collections.CollectionsCaseOpened;
import com.lms.contracts.collections.CollectionsEscalated;
import com.lms.reporting.application.port.out.CollectionCaseRepository;
import com.lms.reporting.domain.CollectionCaseInfo;
import java.util.Objects;
import java.util.UUID;

/** Projects collections.events onto the collection-case read model (ADR 0013). */
public class CollectionCaseProjection {

    private final CollectionCaseRepository cases;

    public CollectionCaseProjection(CollectionCaseRepository cases) {
        this.cases = Objects.requireNonNull(cases);
    }

    public void onOpened(CollectionsCaseOpened e) {
        cases.upsert(
                new CollectionCaseInfo(
                        UUID.fromString(e.getLoanId()),
                        e.getCaseId(),
                        e.getBucket(),
                        "OPEN",
                        e.getOpenedAt()));
    }

    public void onEscalated(CollectionsEscalated e) {
        cases.upsert(
                new CollectionCaseInfo(
                        UUID.fromString(e.getLoanId()), e.getCaseId(), null, e.getStatus(), null));
    }

    public void onClosed(CollectionsCaseClosed e) {
        cases.upsert(
                new CollectionCaseInfo(
                        UUID.fromString(e.getLoanId()), e.getCaseId(), null, "CLOSED", null));
    }
}
