package com.lms.customer.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.customer.application.port.out.KycClient;
import com.lms.customer.application.port.out.KycRepository;
import com.lms.customer.domain.KycStatus;
import com.lms.customer.testsupport.FakeCustomerEventPublisher;
import com.lms.security.crypto.BlindIndex;
import com.lms.security.crypto.FieldCipher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KycServiceTest {

    private final FieldCipher cipher = new FieldCipher(new byte[32]);
    private final BlindIndex blindIndex = new BlindIndex(new byte[32]);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC);
    private final FakeCustomerEventPublisher events = new FakeCustomerEventPublisher();

    private static final class FakeKycRepo implements KycRepository {
        String lastEncryptedId;
        String lastBlindIndex;
        KycStatus status;

        @Override
        public void save(String customerId, KycStatus st, String enc, String bi, Instant at) {
            this.lastEncryptedId = enc;
            this.lastBlindIndex = bi;
            this.status = st;
        }

        @Override
        public void updateStatus(String customerId, KycStatus st, Instant at) {
            this.status = st;
        }

        @Override
        public Optional<KycStatus> statusOf(String customerId) {
            return Optional.ofNullable(status);
        }
    }

    private final FakeKycRepo repo = new FakeKycRepo();

    private KycService service(KycStatus simResult) {
        KycClient client = (subjectId, callbackUrl) -> simResult;
        return new KycService(client, repo, events, cipher, blindIndex, clock, "http://cb");
    }

    @Test
    void verifiedStoresCiphertextAndPublishes() {
        KycStatus status = service(KycStatus.VERIFIED).submit("cust-1", "ABCDE1234F");
        assertThat(status).isEqualTo(KycStatus.VERIFIED);
        // Stored value is ciphertext, not the plaintext id, and decrypts back.
        assertThat(repo.lastEncryptedId).isNotEqualTo("ABCDE1234F");
        assertThat(cipher.decrypt(repo.lastEncryptedId)).isEqualTo("ABCDE1234F");
        assertThat(repo.lastBlindIndex).isEqualTo(blindIndex.compute("ABCDE1234F"));
        assertThat(events.kycCompleted).containsExactly("cust-1:VERIFIED");
    }

    @Test
    void pendingDoesNotPublishUntilCallback() {
        KycService service = service(KycStatus.PENDING);
        assertThat(service.submit("cust-2", "ABCDE1234F")).isEqualTo(KycStatus.PENDING);
        assertThat(events.kycCompleted).isEmpty();

        service.onCallback("cust-2", KycStatus.VERIFIED);
        assertThat(events.kycCompleted).containsExactly("cust-2:VERIFIED");
    }
}
