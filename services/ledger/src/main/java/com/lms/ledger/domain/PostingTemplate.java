package com.lms.ledger.domain;

import java.util.List;

/**
 * A versioned posting template selected by event type (docs/domain-rules.md "Posting templates").
 * Each template turns a source event's amounts into the balanced debit/credit lines for that event.
 * New templates (repayment, penal, write-off) plug in by implementing this interface and
 * registering under their event type (Phases 3–4).
 */
public interface PostingTemplate {

    /** The source event type this template handles, e.g. {@code disbursement.succeeded}. */
    String eventType();

    /** Monotonic template version, bumped if the posting rule changes. */
    int version();

    /** The balanced postings for the event (the {@link JournalEntry} re-checks the balance). */
    List<Posting> build(PostingInput input);
}
