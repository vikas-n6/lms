package com.lms.customer.adapters.out.persistence;

import com.lms.customer.application.port.out.BankAccountRepository;
import com.lms.customer.domain.BankAccount;
import com.lms.customer.domain.CustomerId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** JDBC persistence for bank accounts; account number stored only as ciphertext + blind index. */
@Repository
public class JdbcBankAccountRepository implements BankAccountRepository {

    private final JdbcClient jdbc;

    public JdbcBankAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(BankAccount a, String encryptedAccountNumber, String accountBlindIndex) {
        jdbc.sql(
                        """
                        insert into bank_accounts
                          (id, customer_id, masked_number, ifsc, account_encrypted, account_blind_index,
                           penny_drop_verified, linked_at, created_at, updated_at, version)
                        values (:id, :cust, :masked, :ifsc, :enc, :bi, :verified, :linkedAt, :linkedAt,
                                :linkedAt, 0)
                        """)
                .param("id", a.id())
                .param("cust", a.customerId().value())
                .param("masked", a.maskedNumber())
                .param("ifsc", a.ifsc())
                .param("enc", encryptedAccountNumber)
                .param("bi", accountBlindIndex)
                .param("verified", a.pennyDropVerified())
                .param("linkedAt", java.sql.Timestamp.from(a.linkedAt()))
                .update();
    }

    @Override
    public Optional<BankAccount> findByCustomer(CustomerId customerId) {
        return jdbc.sql(
                        """
                        select id, customer_id, masked_number, ifsc, penny_drop_verified, linked_at
                        from bank_accounts where customer_id = :cust order by linked_at desc limit 1
                        """)
                .param("cust", customerId.value())
                .query(JdbcBankAccountRepository::mapRow)
                .optional();
    }

    private static BankAccount mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new BankAccount(
                rs.getObject("id", UUID.class),
                new CustomerId(rs.getObject("customer_id", UUID.class)),
                rs.getString("masked_number"),
                rs.getString("ifsc"),
                rs.getBoolean("penny_drop_verified"),
                rs.getTimestamp("linked_at").toInstant());
    }
}
