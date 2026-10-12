package com.lms.customer.adapters.out.persistence;

import com.lms.customer.application.port.out.KycRepository;
import com.lms.customer.domain.KycStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** JDBC persistence for KYC records; id number stored only as ciphertext + blind index. */
@Repository
public class JdbcKycRepository implements KycRepository {

    private final JdbcClient jdbc;

    public JdbcKycRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(
            String customerId, KycStatus status, String enc, String blindIndex, Instant at) {
        jdbc.sql(
                        """
                        insert into kyc_records
                          (customer_id, status, id_number_encrypted, id_number_blind_index,
                           created_at, updated_at, version)
                        values (:id, :status, :enc, :bi, :at, :at, 0)
                        on conflict (customer_id) do update set
                          status = excluded.status, id_number_encrypted = excluded.id_number_encrypted,
                          id_number_blind_index = excluded.id_number_blind_index,
                          updated_at = excluded.updated_at, version = kyc_records.version + 1
                        """)
                .param("id", customerId)
                .param("status", status.name())
                .param("enc", enc)
                .param("bi", blindIndex)
                .param("at", java.sql.Timestamp.from(at))
                .update();
    }

    @Override
    public void updateStatus(String customerId, KycStatus status, Instant at) {
        jdbc.sql(
                        "update kyc_records set status = :status, updated_at = :at, "
                                + "version = version + 1 where customer_id = :id")
                .param("status", status.name())
                .param("at", java.sql.Timestamp.from(at))
                .param("id", customerId)
                .update();
    }

    @Override
    public Optional<KycStatus> statusOf(String customerId) {
        return jdbc.sql("select status from kyc_records where customer_id = :id")
                .param("id", customerId)
                .query((rs, n) -> KycStatus.valueOf(rs.getString("status")))
                .optional();
    }
}
