package com.lms.decision.application;

import com.lms.concurrency.Fanout;
import com.lms.concurrency.FanoutException;
import com.lms.concurrency.FanoutTimeoutException;
import com.lms.decision.application.port.out.BankClient;
import com.lms.decision.application.port.out.BureauClient;
import com.lms.decision.application.port.out.MarketDataProvider;
import com.lms.money.Money;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Fans out the bureau pull and the bank statement-analysis in parallel (phase prompt S5), each
 * gated by a 2-second timeout via {@link Fanout} (structured concurrency, confined to
 * lms-concurrency).
 *
 * <p>Each task catches its own failure and returns an "unavailable" probe, so one provider failing
 * does not abort the other — the decision still completes. If the whole fan-out times out or is
 * interrupted, the bureau is treated as unavailable so an application never hangs (domain-rules).
 */
public final class BureauBankFanout implements MarketDataProvider {

    private final BureauClient bureauClient;
    private final BankClient bankClient;
    private final Duration timeout;

    public BureauBankFanout(BureauClient bureauClient, BankClient bankClient) {
        this(bureauClient, bankClient, Duration.ofSeconds(2));
    }

    public BureauBankFanout(BureauClient bureauClient, BankClient bankClient, Duration timeout) {
        this.bureauClient = bureauClient;
        this.bankClient = bankClient;
        this.timeout = timeout;
    }

    @Override
    public MarketData fetch(String subjectId, String phone) {
        Callable<Probe> bureauTask =
                () -> {
                    try {
                        BureauClient.BureauReport r = bureauClient.pull(subjectId, phone);
                        return new BureauProbe(true, r.score(), r.overdue(), r.activeAccounts());
                    } catch (RuntimeException e) {
                        return new BureauProbe(false, 0, false, 0);
                    }
                };
        Callable<Probe> bankTask =
                () -> {
                    try {
                        return new BankProbe(
                                Optional.of(bankClient.estimatedMonthlyIncome(subjectId)));
                    } catch (RuntimeException e) {
                        return new BankProbe(Optional.empty());
                    }
                };

        try {
            List<Probe> probes = Fanout.callAll(timeout, List.of(bureauTask, bankTask));
            BureauProbe bureau = null;
            BankProbe bank = null;
            for (Probe probe : probes) {
                switch (probe) {
                    case BureauProbe b -> bureau = b;
                    case BankProbe b -> bank = b;
                }
            }
            return new MarketData(
                    bureau.available(),
                    bureau.score(),
                    bureau.overdue(),
                    bureau.activeAccounts(),
                    bank.income());
        } catch (FanoutTimeoutException | FanoutException e) {
            return MarketData.bureauUnavailable(Optional.empty());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return MarketData.bureauUnavailable(Optional.empty());
        }
    }

    private sealed interface Probe permits BureauProbe, BankProbe {}

    private record BureauProbe(boolean available, int score, boolean overdue, int activeAccounts)
            implements Probe {}

    private record BankProbe(Optional<Money> income) implements Probe {}
}
