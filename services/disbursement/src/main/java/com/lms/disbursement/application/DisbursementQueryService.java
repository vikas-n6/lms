package com.lms.disbursement.application;

import com.lms.disbursement.application.port.out.DisbursementRepository;
import com.lms.disbursement.application.port.out.PayoutAttemptRepository;
import com.lms.disbursement.domain.Disbursement;
import com.lms.disbursement.domain.PayoutAttempt;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Read side for the admin disbursement monitor (list, detail with attempts). */
public class DisbursementQueryService {

    private final DisbursementRepository disbursements;
    private final PayoutAttemptRepository attempts;

    public DisbursementQueryService(
            DisbursementRepository disbursements, PayoutAttemptRepository attempts) {
        this.disbursements = Objects.requireNonNull(disbursements);
        this.attempts = Objects.requireNonNull(attempts);
    }

    public List<Disbursement> list() {
        return disbursements.findAll();
    }

    public Disbursement get(UUID id) {
        return disbursements.findById(id).orElseThrow(() -> new DisbursementNotFoundException(id));
    }

    public List<PayoutAttempt> attempts(UUID disbursementId) {
        return attempts.findByDisbursementId(disbursementId);
    }
}
