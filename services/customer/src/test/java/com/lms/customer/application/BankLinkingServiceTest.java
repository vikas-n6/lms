package com.lms.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.customer.application.port.out.BankAccountRepository;
import com.lms.customer.application.port.out.BankVerificationClient;
import com.lms.customer.domain.BankAccount;
import com.lms.customer.domain.CustomerId;
import com.lms.customer.testsupport.FakeCustomerEventPublisher;
import com.lms.security.crypto.BlindIndex;
import com.lms.security.crypto.FieldCipher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BankLinkingServiceTest {

    private final FieldCipher cipher = new FieldCipher(new byte[32]);
    private final BlindIndex blindIndex = new BlindIndex(new byte[32]);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC);
    private final FakeCustomerEventPublisher events = new FakeCustomerEventPublisher();

    private static final class FakeBankRepo implements BankAccountRepository {
        String lastEncrypted;
        BankAccount saved;

        @Override
        public void save(BankAccount account, String enc, String bi) {
            this.saved = account;
            this.lastEncrypted = enc;
        }

        @Override
        public Optional<BankAccount> findByCustomer(CustomerId id) {
            return Optional.ofNullable(saved);
        }
    }

    private final FakeBankRepo repo = new FakeBankRepo();

    private BankLinkingService service(boolean verified) {
        BankVerificationClient client =
                (accountNumber, ifsc) ->
                        new BankVerificationClient.PennyDrop(
                                verified,
                                "XXXXXX" + accountNumber.substring(accountNumber.length() - 4));
        return new BankLinkingService(client, repo, events, cipher, blindIndex, clock);
    }

    private static final String CUSTOMER_ID = "018f9a00-0000-7000-8000-000000000001";

    @Test
    void linksVerifiedAccountEncryptingTheNumberAndPublishing() {
        BankAccount account = service(true).link(CUSTOMER_ID, "123456789012", "HDFC0001");
        assertThat(account.maskedNumber()).isEqualTo("XXXXXX9012");
        assertThat(account.pennyDropVerified()).isTrue();
        assertThat(repo.lastEncrypted).isNotEqualTo("123456789012");
        assertThat(cipher.decrypt(repo.lastEncrypted)).isEqualTo("123456789012");
        assertThat(events.bankLinked).containsExactly(CUSTOMER_ID + ":XXXXXX9012");
    }

    @Test
    void rejectsFailedPennyDrop() {
        assertThatThrownBy(() -> service(false).link(CUSTOMER_ID, "123456789012", "HDFC0001"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(events.bankLinked).isEmpty();
    }
}
