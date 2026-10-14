import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  useForeclosureQuote,
  useInjectRepayment,
  useLoan,
  useRunAccrual,
  useSimulateDisbursement,
  type Money,
  type ScheduleLine,
} from "../api/loans";
import { formatMoney } from "../lib/money";
import { StateBadge } from "./LoansPage";

function money(m: Money): string {
  return formatMoney(m.minorUnits, m.currency);
}

const STATUS_COLORS: Record<string, string> = {
  UPCOMING: "bg-neutral-100 text-neutral-600",
  DUE: "bg-amber-100 text-amber-800",
  PARTIALLY_PAID: "bg-sky-100 text-sky-800",
  PAID: "bg-green-100 text-green-800",
  OVERDUE: "bg-red-100 text-red-800",
};

export function LoanDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { data, isLoading, isError } = useLoan(id);
  const [showQuote, setShowQuote] = useState(false);

  if (isLoading) return <p className="text-sm text-neutral-500">Loading…</p>;
  if (isError || !data) return <p className="text-sm text-red-700">Could not load loan.</p>;

  const { loan, balances, schedule, history } = data;

  return (
    <section className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/loans" className="text-sm text-blue-700 hover:underline">
            ← Loans
          </Link>
          <h1 className="mt-1 text-xl font-semibold">
            {loan.loanNumber} <StateBadge state={loan.state} />
          </h1>
        </div>
        <button
          onClick={() => setShowQuote(true)}
          className="rounded border border-neutral-300 px-3 py-1.5 text-sm hover:bg-neutral-100"
        >
          Foreclosure quote
        </button>
      </div>

      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <Balance label="Outstanding principal" m={balances.outstandingPrincipal} />
        <Balance label="Interest outstanding" m={balances.interestOutstanding} />
        <Balance label="Penal outstanding" m={balances.penalOutstanding} />
        <Balance label="Total due now" m={balances.totalDueNow} />
      </div>
      <p className="text-sm text-neutral-600">
        Next due date: <span className="font-medium">{balances.nextDueDate ?? "—"}</span>
      </p>

      <DevActions loanId={loan.id} />

      <div>
        <h2 className="mb-2 text-lg font-semibold">EMI schedule</h2>
        <ScheduleTable schedule={schedule} />
      </div>

      <div>
        <h2 className="mb-2 text-lg font-semibold">State history</h2>
        <ol className="space-y-1 text-sm">
          {history.map((h, i) => (
            <li key={i} className="flex items-center gap-2">
              <span className={h.legal ? "text-green-700" : "text-red-700"}>
                {h.legal ? "●" : "✕"}
              </span>
              <span className="font-mono text-xs text-neutral-500">
                {new Date(h.recordedAt).toLocaleString()}
              </span>
              <span>
                {h.fromState ? `${h.fromState} → ` : ""}
                <span className="font-medium">{h.toState}</span>
              </span>
              <span className="text-neutral-500">({h.cause})</span>
            </li>
          ))}
        </ol>
      </div>

      {showQuote && id && <ForeclosureDialog id={id} onClose={() => setShowQuote(false)} />}
    </section>
  );
}

function Balance({ label, m }: { label: string; m: Money }) {
  return (
    <div className="rounded border border-neutral-200 bg-white p-3">
      <div className="text-xs text-neutral-500">{label}</div>
      <div className="mt-1 text-lg font-semibold">{money(m)}</div>
    </div>
  );
}

function ScheduleTable({ schedule }: { schedule: ScheduleLine[] }) {
  return (
    <table className="w-full overflow-hidden rounded border border-neutral-200 bg-white text-sm">
      <thead className="bg-neutral-100 text-left">
        <tr>
          <th className="px-3 py-2 font-medium">#</th>
          <th className="px-3 py-2 font-medium">Due date</th>
          <th className="px-3 py-2 font-medium">Principal</th>
          <th className="px-3 py-2 font-medium">Interest</th>
          <th className="px-3 py-2 font-medium">Paid (P/I)</th>
          <th className="px-3 py-2 font-medium">Penal</th>
          <th className="px-3 py-2 font-medium">Status</th>
        </tr>
      </thead>
      <tbody>
        {schedule.map((line) => (
          <tr
            key={line.instalmentNumber}
            className={`border-t border-neutral-100 ${line.status === "OVERDUE" ? "bg-red-50" : ""}`}
          >
            <td className="px-3 py-2">{line.instalmentNumber}</td>
            <td className="px-3 py-2">{line.dueDate}</td>
            <td className="px-3 py-2">{money(line.principal)}</td>
            <td className="px-3 py-2">{money(line.interest)}</td>
            <td className="px-3 py-2 text-neutral-600">
              {money(line.paidPrincipal)} / {money(line.paidInterest)}
            </td>
            <td className="px-3 py-2 text-neutral-600">
              {money(line.penalPaid)} / {money(line.penalAccrued)}
            </td>
            <td className="px-3 py-2">
              <span
                className={`rounded px-2 py-0.5 text-xs font-medium ${
                  STATUS_COLORS[line.status] ?? "bg-neutral-100 text-neutral-600"
                }`}
              >
                {line.status}
                {line.provisional ? " (prov.)" : ""}
              </span>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function DevActions({ loanId }: { loanId: string }) {
  const simulate = useSimulateDisbursement(loanId);
  const inject = useInjectRepayment(loanId);
  const accrual = useRunAccrual();
  const [amount, setAmount] = useState("");

  return (
    <div className="flex flex-wrap items-center gap-2 rounded border border-dashed border-neutral-300 bg-neutral-50 p-3 text-sm">
      <span className="text-neutral-500">Dev actions:</span>
      <button
        onClick={() => simulate.mutate(undefined)}
        className="rounded border border-neutral-300 px-2 py-1 hover:bg-white"
      >
        Simulate disbursement
      </button>
      <button
        onClick={() => accrual.mutate(undefined)}
        className="rounded border border-neutral-300 px-2 py-1 hover:bg-white"
      >
        Run accrual (today)
      </button>
      <input
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        placeholder="repayment minor units"
        className="w-48 rounded border border-neutral-300 px-2 py-1"
      />
      <button
        disabled={!amount}
        onClick={() => inject.mutate({ amountMinor: Number(amount) })}
        className="rounded border border-neutral-300 px-2 py-1 hover:bg-white disabled:opacity-40"
      >
        Inject repayment
      </button>
    </div>
  );
}

function ForeclosureDialog({ id, onClose }: { id: string; onClose: () => void }) {
  const [date, setDate] = useState("");
  const { data, isLoading, isError } = useForeclosureQuote(id, date || undefined);
  return (
    <div className="fixed inset-0 flex items-center justify-center bg-black/30" onClick={onClose}>
      <div
        className="w-96 rounded bg-white p-5 shadow-lg"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="mb-3 text-lg font-semibold">Foreclosure quote</h3>
        <label className="mb-3 block text-sm text-neutral-600">
          As of{" "}
          <input
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            className="ml-2 rounded border border-neutral-300 px-2 py-1"
          />
        </label>
        {isLoading && <p className="text-sm text-neutral-500">Loading…</p>}
        {isError && <p className="text-sm text-red-700">Loan is not disbursed or quote unavailable.</p>}
        {data && (
          <p className="text-2xl font-semibold">
            {money(data.amount)}
            <span className="ml-2 text-sm font-normal text-neutral-500">on {data.asOf}</span>
          </p>
        )}
        <div className="mt-4 text-right">
          <button onClick={onClose} className="rounded border border-neutral-300 px-3 py-1 text-sm">
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
