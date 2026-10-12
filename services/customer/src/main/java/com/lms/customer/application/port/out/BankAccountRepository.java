package com.lms.customer.application.port.out;

import com.lms.customer.domain.BankAccount;
import com.lms.customer.domain.CustomerId;
import java.util.Optional;

/**
 * Persistence for linked bank accounts. The account number is stored only as ciphertext plus a
 * keyed blind index for lookup — never in plaintext (CLAUDE.md).
 */
public interface BankAccountRepository {

    void save(BankAccount account, String encryptedAccountNumber, String accountBlindIndex);

    Optional<BankAccount> findByCustomer(CustomerId customerId);
}
