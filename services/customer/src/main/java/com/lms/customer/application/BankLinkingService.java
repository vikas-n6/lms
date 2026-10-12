package com.lms.customer.application;

import com.lms.customer.application.port.out.BankAccountRepository;
import com.lms.customer.application.port.out.BankVerificationClient;
import com.lms.customer.application.port.out.CustomerEventPublisher;
import com.lms.customer.domain.BankAccount;
import com.lms.customer.domain.CustomerId;
import com.lms.security.crypto.BlindIndex;
import com.lms.security.crypto.FieldCipher;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Links a payout bank account after a penny-drop (phase prompt S3). The account number is encrypted
 * (AES-GCM) with a keyed blind index; only the masked number is stored in the domain, published or
 * logged. Publishes {@code customer.bank-account.linked} when the penny-drop verifies.
 */
@Service
public class BankLinkingService {

    private final BankVerificationClient bankClient;
    private final BankAccountRepository repository;
    private final CustomerEventPublisher events;
    private final FieldCipher accountCipher;
    private final BlindIndex accountBlindIndex;
    private final Clock clock;

    public BankLinkingService(
            BankVerificationClient bankClient,
            BankAccountRepository repository,
            CustomerEventPublisher events,
            FieldCipher accountNumberCipher,
            BlindIndex accountNumberBlindIndex,
            Clock clock) {
        this.bankClient = bankClient;
        this.repository = repository;
        this.events = events;
        this.accountCipher = accountNumberCipher;
        this.accountBlindIndex = accountNumberBlindIndex;
        this.clock = clock;
    }

    @Transactional
    public BankAccount link(String customerId, String accountNumber, String ifsc) {
        BankVerificationClient.PennyDrop pennyDrop = bankClient.pennyDrop(accountNumber, ifsc);
        if (!pennyDrop.verified()) {
            throw new IllegalArgumentException("penny-drop verification failed");
        }
        BankAccount account =
                BankAccount.linked(
                        clock,
                        CustomerId.of(customerId),
                        pennyDrop.maskedAccountNumber(),
                        ifsc,
                        true);
        repository.save(
                account,
                accountCipher.encrypt(accountNumber),
                accountBlindIndex.compute(accountNumber));
        events.bankAccountLinked(customerId, account);
        return account;
    }
}
