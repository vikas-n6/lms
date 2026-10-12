package com.lms.decision.adapters.in.kafka;

import com.lms.contracts.application.ApplicantSnapshot;
import com.lms.contracts.application.ApplicationSubmitted;
import com.lms.contracts.events.MoneyAmount;
import com.lms.decision.application.ApplicationForDecision;
import com.lms.decision.application.DecisionService;
import com.lms.money.Money;
import com.lms.outbox.inbox.IdempotentConsumer;
import java.util.Currency;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code application.submitted} from {@code application.events} and runs the decision
 * exactly once (inbox idempotency, in the handler's transaction so offsets commit only after it
 * commits — docs/architecture.md). Other event types on the topic are ignored. The self-contained
 * snapshot is mapped to the application facts so decision never calls customer/origination over
 * REST.
 */
@Component
public class ApplicationSubmittedListener {

    static final String TOPIC = "application.events";

    private final DecisionService decisionService;
    private final IdempotentConsumer idempotent;

    public ApplicationSubmittedListener(
            DecisionService decisionService, IdempotentConsumer idempotent) {
        this.decisionService = decisionService;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "decision")
    @Transactional
    public void onMessage(Object payload) {
        if (!(payload instanceof ApplicationSubmitted event)) {
            return; // not an application.submitted event
        }
        UUID eventId = UUID.fromString(event.getMetadata().getEventId());
        idempotent.process(eventId, TOPIC, () -> decisionService.decide(toApplication(event)));
    }

    private static ApplicationForDecision toApplication(ApplicationSubmitted event) {
        ApplicantSnapshot s = event.getSnapshot();
        return new ApplicationForDecision(
                event.getApplicationId(),
                event.getCustomerId(),
                event.getCustomerId(), // bureau/bank subject id
                s.getBureauSubjectPhone(),
                s.getPhoneBlindIndex(),
                money(event.getRequestedAmount()),
                s.getAgeYears(),
                "VERIFIED".equals(s.getKycStatus()),
                money(s.getDeclaredMonthlyIncome()),
                money(s.getExistingMonthlyEmi()),
                s.getActiveLoanCount());
    }

    private static Money money(MoneyAmount amount) {
        return Money.of(amount.getMinorUnits(), Currency.getInstance(amount.getCurrency()));
    }
}
