package com.lms.ledger.adapters.out.persistence;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import com.lms.ledger.application.port.out.JournalEntryRepository;
import com.lms.ledger.domain.AccountCode;
import com.lms.ledger.domain.JournalEntry;
import com.lms.ledger.domain.Posting;
import com.lms.ledger.domain.PostingSide;
import com.lms.money.Money;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * jOOQ persistence for journal entries and postings (locked "jOOQ for the ledger", used via the
 * programmatic DSL with no code generation — ADR 0007). Entries and postings are only ever inserted
 * or read; there is no update or delete path (docs/domain-rules.md "Ledger invariants").
 */
@Repository
public class JooqJournalEntryRepository implements JournalEntryRepository {

    private static final org.jooq.Table<Record> ENTRIES = table(name("journal_entries"));
    private static final org.jooq.Table<Record> POSTINGS = table(name("postings"));

    private static final org.jooq.Field<UUID> E_ID = field(name("id"), UUID.class);
    private static final org.jooq.Field<UUID> E_SOURCE_EVENT =
            field(name("source_event_id"), UUID.class);
    private static final org.jooq.Field<String> E_SOURCE_TYPE =
            field(name("source_event_type"), String.class);
    private static final org.jooq.Field<String> E_CURRENCY = field(name("currency"), String.class);
    private static final org.jooq.Field<String> E_LOAN = field(name("loan_id"), String.class);
    private static final org.jooq.Field<String> E_CUSTOMER =
            field(name("customer_id"), String.class);
    private static final org.jooq.Field<UUID> E_REVERSES =
            field(name("reverses_entry_id"), UUID.class);
    private static final org.jooq.Field<OffsetDateTime> E_OCCURRED =
            field(name("occurred_at"), OffsetDateTime.class);

    private static final org.jooq.Field<UUID> P_ID = field(name("id"), UUID.class);
    private static final org.jooq.Field<UUID> P_ENTRY = field(name("journal_entry_id"), UUID.class);
    private static final org.jooq.Field<String> P_ACCOUNT =
            field(name("account_code"), String.class);
    private static final org.jooq.Field<String> P_SIDE = field(name("side"), String.class);
    private static final org.jooq.Field<Long> P_AMOUNT = field(name("amount_minor"), Long.class);
    private static final org.jooq.Field<String> P_CURRENCY = field(name("currency"), String.class);
    private static final org.jooq.Field<String> P_LOAN = field(name("loan_id"), String.class);
    private static final org.jooq.Field<String> P_CUSTOMER =
            field(name("customer_id"), String.class);

    private final DSLContext dsl;

    public JooqJournalEntryRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    @Override
    public boolean existsForSourceEvent(UUID sourceEventId) {
        return dsl.fetchExists(
                dsl.selectOne().from(ENTRIES).where(E_SOURCE_EVENT.eq(sourceEventId)));
    }

    @Override
    public boolean isReversed(UUID entryId) {
        return dsl.fetchExists(dsl.selectOne().from(ENTRIES).where(E_REVERSES.eq(entryId)));
    }

    @Override
    @Transactional
    public void save(JournalEntry entry) {
        dsl.insertInto(ENTRIES)
                .columns(
                        E_ID,
                        E_SOURCE_EVENT,
                        E_SOURCE_TYPE,
                        E_CURRENCY,
                        E_LOAN,
                        E_CUSTOMER,
                        E_REVERSES,
                        E_OCCURRED)
                .values(
                        entry.id(),
                        entry.sourceEventId(),
                        entry.sourceEventType(),
                        entry.currency().getCurrencyCode(),
                        entry.loanId(),
                        entry.customerId(),
                        entry.reversesEntryId(),
                        entry.occurredAt().atOffset(ZoneOffset.UTC))
                .execute();
        for (Posting posting : entry.postings()) {
            dsl.insertInto(POSTINGS)
                    .columns(
                            P_ID,
                            P_ENTRY,
                            P_ACCOUNT,
                            P_SIDE,
                            P_AMOUNT,
                            P_CURRENCY,
                            P_LOAN,
                            P_CUSTOMER)
                    .values(
                            UUID.randomUUID(),
                            entry.id(),
                            posting.account().code(),
                            posting.side().name(),
                            posting.amount().minorUnits(),
                            posting.amount().currency().getCurrencyCode(),
                            posting.loanId(),
                            posting.customerId())
                    .execute();
        }
    }

    @Override
    public Optional<JournalEntry> findById(UUID id) {
        return dsl.selectFrom(ENTRIES).where(E_ID.eq(id)).fetchOptional().map(this::toEntry);
    }

    @Override
    public List<JournalEntry> find(
            Optional<String> loanId, Optional<LocalDate> date, Optional<String> accountCode) {
        Condition condition = DSL.noCondition();
        if (loanId.isPresent()) {
            condition = condition.and(E_LOAN.eq(loanId.get()));
        }
        if (date.isPresent()) {
            condition =
                    condition
                            .and(E_OCCURRED.ge(date.get().atStartOfDay().atOffset(ZoneOffset.UTC)))
                            .and(
                                    E_OCCURRED.lt(
                                            date.get()
                                                    .plusDays(1)
                                                    .atStartOfDay()
                                                    .atOffset(ZoneOffset.UTC)));
        }
        if (accountCode.isPresent()) {
            condition =
                    condition.and(
                            E_ID.in(
                                    dsl.select(P_ENTRY)
                                            .from(POSTINGS)
                                            .where(P_ACCOUNT.eq(accountCode.get()))));
        }
        return dsl.selectFrom(ENTRIES)
                .where(condition)
                .orderBy(E_OCCURRED.desc())
                .fetch()
                .map(this::toEntry);
    }

    @Override
    public List<JournalEntry> findPostedUpTo(LocalDate asOf) {
        return dsl.selectFrom(ENTRIES)
                .where(E_OCCURRED.lt(asOf.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)))
                .orderBy(E_OCCURRED.asc())
                .fetch()
                .map(this::toEntry);
    }

    @Override
    public List<JournalEntry> findAll() {
        return dsl.selectFrom(ENTRIES).orderBy(E_OCCURRED.asc()).fetch().map(this::toEntry);
    }

    private JournalEntry toEntry(Record e) {
        UUID id = e.get(E_ID);
        Currency currency = Currency.getInstance(e.get(E_CURRENCY));
        List<Posting> postings = new ArrayList<>();
        for (Record p : dsl.selectFrom(POSTINGS).where(P_ENTRY.eq(id)).fetch()) {
            postings.add(
                    new Posting(
                            AccountCode.ofCode(p.get(P_ACCOUNT)),
                            PostingSide.valueOf(p.get(P_SIDE)),
                            Money.of(p.get(P_AMOUNT), Currency.getInstance(p.get(P_CURRENCY))),
                            p.get(P_LOAN),
                            p.get(P_CUSTOMER)));
        }
        return new JournalEntry(
                id,
                e.get(E_SOURCE_EVENT),
                e.get(E_SOURCE_TYPE),
                currency,
                postings,
                e.get(E_LOAN),
                e.get(E_CUSTOMER),
                e.get(E_REVERSES),
                e.get(E_OCCURRED).toInstant());
    }
}
