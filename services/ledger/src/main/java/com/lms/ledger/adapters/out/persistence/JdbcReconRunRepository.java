package com.lms.ledger.adapters.out.persistence;

import com.lms.ledger.application.port.out.ReconRunRepository;
import com.lms.ledger.domain.recon.ReconClassification;
import com.lms.ledger.domain.recon.ReconItem;
import com.lms.ledger.domain.recon.ReconResult;
import com.lms.money.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Spring JDBC persistence for bank/rail reconciliation runs and items (phase prompt task 6). */
@Repository
public class JdbcReconRunRepository implements ReconRunRepository {

    private final JdbcClient jdbc;

    public JdbcReconRunRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional
    public UUID saveRun(LocalDate reconDate, String currency, ReconResult result, Instant runAt) {
        UUID runId = UUID.randomUUID();
        jdbc.sql(
                        """
                        insert into recon_runs (id, run_at, diff_minor_units, kind, recon_date,
                            currency, matched, missing_in_ledger, missing_in_bank, amount_mismatch,
                            duplicate)
                        values (:id, :runAt, 0, 'BANK_RAIL', :date, :currency, :matched, :mil,
                            :mib, :am, :dup)
                        """)
                .param("id", runId)
                .param("runAt", java.sql.Timestamp.from(runAt))
                .param("date", reconDate)
                .param("currency", currency)
                .param("matched", result.count(ReconClassification.MATCHED))
                .param("mil", result.count(ReconClassification.MISSING_IN_LEDGER))
                .param("mib", result.count(ReconClassification.MISSING_IN_BANK))
                .param("am", result.count(ReconClassification.AMOUNT_MISMATCH))
                .param("dup", result.count(ReconClassification.DUPLICATE))
                .update();
        for (ReconItem item : result.items()) {
            jdbc.sql(
                            """
                            insert into recon_items (id, run_id, reference, classification,
                                external_amount_minor, ledger_amount_minor, currency)
                            values (:id, :runId, :ref, :class, :ext, :led, :currency)
                            """)
                    .param("id", UUID.randomUUID())
                    .param("runId", runId)
                    .param("ref", item.reference())
                    .param("class", item.classification().name())
                    .param("ext", item.externalAmount().map(Money::minorUnits).orElse(null))
                    .param("led", item.ledgerAmount().map(Money::minorUnits).orElse(null))
                    .param("currency", currency)
                    .update();
        }
        return runId;
    }

    @Override
    public void resolveItem(UUID itemId, String note) {
        jdbc.sql(
                        """
                        update recon_items set resolved = true, resolution_note = :note,
                            updated_at = now(), version = version + 1
                        where id = :id
                        """)
                .param("note", note)
                .param("id", itemId)
                .update();
    }

    @Override
    public List<Run> listRuns() {
        return jdbc.sql(
                        """
                        select id, recon_date, currency, matched, missing_in_ledger,
                               missing_in_bank, amount_mismatch, duplicate, run_at
                          from recon_runs where kind = 'BANK_RAIL' order by run_at desc
                        """)
                .query(
                        (rs, n) ->
                                new Run(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("recon_date", LocalDate.class),
                                        rs.getString("currency"),
                                        rs.getInt("matched"),
                                        rs.getInt("missing_in_ledger"),
                                        rs.getInt("missing_in_bank"),
                                        rs.getInt("amount_mismatch"),
                                        rs.getInt("duplicate"),
                                        rs.getTimestamp("run_at").toInstant()))
                .list();
    }

    @Override
    public List<StoredItem> itemsForRun(UUID runId) {
        return jdbc.sql(SELECT_ITEM + " where run_id = :runId order by classification, reference")
                .param("runId", runId)
                .query(JdbcReconRunRepository::mapItem)
                .list();
    }

    @Override
    public Optional<StoredItem> findItem(UUID itemId) {
        return jdbc.sql(SELECT_ITEM + " where id = :id")
                .param("id", itemId)
                .query(JdbcReconRunRepository::mapItem)
                .optional();
    }

    private static final String SELECT_ITEM =
            """
            select id, run_id, reference, classification, external_amount_minor,
                   ledger_amount_minor, currency, resolved, resolution_note
            from recon_items
            """;

    private static StoredItem mapItem(ResultSet rs, int rowNum) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        Long ext = (Long) rs.getObject("external_amount_minor");
        Long led = (Long) rs.getObject("ledger_amount_minor");
        ReconItem item =
                new ReconItem(
                        rs.getString("reference"),
                        ReconClassification.valueOf(rs.getString("classification")),
                        Optional.ofNullable(ext).map(a -> Money.of(a, currency)),
                        Optional.ofNullable(led).map(a -> Money.of(a, currency)));
        return new StoredItem(
                rs.getObject("id", UUID.class),
                rs.getObject("run_id", UUID.class),
                item,
                rs.getBoolean("resolved"),
                rs.getString("resolution_note"));
    }
}
