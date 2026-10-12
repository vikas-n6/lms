package com.lms.decision.application;

import com.lms.decision.application.port.out.DecisionRepository;
import com.lms.decision.domain.Decision;
import com.lms.decision.domain.DecisionId;
import org.springframework.stereotype.Service;

/** Read side for the decision explainer and the review flow. */
@Service
public class DecisionQueryService {

    private final DecisionRepository repository;

    public DecisionQueryService(DecisionRepository repository) {
        this.repository = repository;
    }

    public Decision byId(String id) {
        return repository
                .findById(DecisionId.of(id))
                .orElseThrow(() -> new DecisionNotFoundException(id));
    }
}
