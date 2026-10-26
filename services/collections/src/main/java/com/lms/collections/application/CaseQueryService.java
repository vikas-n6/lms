package com.lms.collections.application;

import com.lms.collections.application.port.out.CollectionCaseRepository;
import com.lms.collections.application.port.out.DunningActionRepository;
import com.lms.collections.domain.CollectionCase;
import com.lms.collections.domain.DunningAction;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Read model for the collections APIs (phase prompt task 4): case list/filter and case detail. */
public class CaseQueryService {

    private final CollectionCaseRepository cases;
    private final DunningActionRepository actions;

    public CaseQueryService(CollectionCaseRepository cases, DunningActionRepository actions) {
        this.cases = Objects.requireNonNull(cases);
        this.actions = Objects.requireNonNull(actions);
    }

    public List<CollectionCase> list(
            Optional<String> bucket, Optional<String> status, Optional<String> agent) {
        return cases.find(bucket, status, agent);
    }

    public Optional<CaseDetail> detail(UUID caseId) {
        return cases.findById(caseId).map(c -> new CaseDetail(c, actions.findByCaseId(caseId)));
    }

    /** A case with its dunning action timeline. */
    public record CaseDetail(CollectionCase theCase, List<DunningAction> timeline) {}
}
