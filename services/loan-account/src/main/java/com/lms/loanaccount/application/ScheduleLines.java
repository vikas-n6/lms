package com.lms.loanaccount.application;

import com.lms.loanaccount.domain.LoanScheduleLine;
import com.lms.money.Money;
import com.lms.schedule.Schedule;
import com.lms.schedule.ScheduleItem;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Maps an {@link Schedule} into persistable {@link LoanScheduleLine}s with zeroed paid/penal. */
final class ScheduleLines {

    private ScheduleLines() {}

    static List<LoanScheduleLine> from(UUID loanId, Schedule schedule, boolean provisional) {
        Money zero = Money.zero(schedule.principal().currency());
        List<LoanScheduleLine> lines = new ArrayList<>(schedule.items().size());
        for (ScheduleItem item : schedule.items()) {
            lines.add(
                    new LoanScheduleLine(
                            loanId,
                            item.instalmentNumber(),
                            item.dueDate(),
                            item.principal(),
                            item.interest(),
                            zero,
                            zero,
                            zero,
                            zero,
                            provisional));
        }
        return lines;
    }
}
