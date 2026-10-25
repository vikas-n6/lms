package com.lms.repayment.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.repayment.application.RecordRepaymentService;
import com.lms.repayment.application.RepaymentFakes;
import com.lms.repayment.application.ReverseRepaymentService;
import com.lms.security.callback.CallbackSigner;
import com.lms.security.callback.CallbackVerifier;
import com.lms.security.callback.InvalidCallbackSignatureException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MandatesCallbackControllerTest {

    private static final String SECRET = "local-dev-callback-secret";
    private static final Instant NOW = Instant.parse("2026-11-13T06:00:00Z");
    private static final UUID LOAN = UUID.randomUUID();

    private final CallbackSigner signer = new CallbackSigner(SECRET.getBytes());
    private RepaymentFakes.RecordingPublisher publisher;
    private MandatesCallbackController controller;

    @BeforeEach
    void setUp() {
        var repayments = new RepaymentFakes.FakeRepaymentRepository();
        var dedup = new RepaymentFakes.FakeDedup();
        publisher = new RepaymentFakes.RecordingPublisher();
        var recordService = new RecordRepaymentService(repayments, publisher, dedup, fixedClock());
        var reverseService =
                new ReverseRepaymentService(repayments, publisher, dedup, fixedClock());
        CallbackVerifier verifier =
                CallbackVerifier.fromSecret(SECRET, fixedClock(), Duration.ofMinutes(5));
        BusinessCalendarStub calendar = new BusinessCalendarStub();
        controller =
                new MandatesCallbackController(
                        verifier,
                        recordService,
                        reverseService,
                        publisher,
                        calendar,
                        JsonMapper.builder().build(),
                        2);
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private String body(String status, String code) {
        return "{\"idempotencyKey\":\"debit-1\",\"mandateId\":\"MND-1\",\"loanId\":\""
                + LOAN
                + "\",\"amountMinor\":474000,\"currency\":\"INR\",\"status\":\""
                + status
                + "\",\"failureCode\":"
                + (code == null ? "null" : "\"" + code + "\"")
                + "}";
    }

    @Test
    void invalidSignatureRejected() {
        assertThatThrownBy(() -> controller.mandates("t=1,v1=bad", body("SUCCESS", null)))
                .isInstanceOf(InvalidCallbackSignatureException.class);
    }

    @Test
    void successRecordsRepaymentReceived() {
        String b = body("SUCCESS", null);
        controller.mandates(signer.sign(NOW, b), b);
        assertThat(publisher.events).contains("repayment.received:debit-1");
    }

    @Test
    void failurePublishesDebitFailedWithRepresentationDate() {
        String b = body("FAILED", "INSUFFICIENT_FUNDS");
        controller.mandates(signer.sign(NOW, b), b);
        // 2026-11-13 is a Friday; +2 business days -> Tuesday 2026-11-17.
        assertThat(publisher.events)
                .anyMatch(e -> e.startsWith("mandate.debit-failed:" + LOAN + ":2026-11-17"));
    }

    private static final class BusinessCalendarStub implements com.lms.config.BusinessCalendar {
        @Override
        public LocalDate today() {
            return LocalDate.of(2026, 11, 13);
        }
    }
}
