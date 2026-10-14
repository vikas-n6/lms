import { useState } from "react";
import { Link } from "react-router-dom";
import { useLoans } from "../api/loans";
import { formatMoney } from "../lib/money";

const STATES = [
  "",
  "CREATED",
  "PENDING_DISBURSAL",
  "ACTIVE",
  "DELINQUENT",
  "CLOSED",
  "FORECLOSED",
  "WRITTEN_OFF",
  "CANCELLED",
];

export function LoansPage() {
  const [state, setState] = useState("");
  const { data, isLoading, isError } = useLoans({ state: state || undefined });

  return (
    <section>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-semibold">Loans</h1>
      </div>
      <label className="mb-3 block text-sm text-neutral-600">
        Filter state{" "}
        <select
          className="ml-2 rounded border border-neutral-300 px-2 py-1"
          value={state}
          onChange={(e) => setState(e.target.value)}
        >
          {STATES.map((s) => (
            <option key={s} value={s}>
              {s || "all"}
            </option>
          ))}
        </select>
      </label>
      {isLoading && <p className="text-sm text-neutral-500">Loading…</p>}
      {isError && <p className="text-sm text-red-700">Could not load loans.</p>}
      {data && (
        <table className="w-full overflow-hidden rounded border border-neutral-200 bg-white text-sm">
          <thead className="bg-neutral-100 text-left">
            <tr>
              <th className="px-4 py-2 font-medium">Loan number</th>
              <th className="px-4 py-2 font-medium">State</th>
              <th className="px-4 py-2 font-medium">Principal</th>
              <th className="px-4 py-2 font-medium">Tenor</th>
              <th className="px-4 py-2 font-medium">Disbursed</th>
            </tr>
          </thead>
          <tbody>
            {data.map((loan) => (
              <tr key={loan.id} className="border-t border-neutral-100">
                <td className="px-4 py-2 font-mono text-xs">
                  <Link className="text-blue-700 hover:underline" to={`/loans/${loan.id}`}>
                    {loan.loanNumber}
                  </Link>
                </td>
                <td className="px-4 py-2">
                  <StateBadge state={loan.state} />
                </td>
                <td className="px-4 py-2">
                  {formatMoney(loan.principal.minorUnits, loan.principal.currency)}
                </td>
                <td className="px-4 py-2">{loan.tenorMonths} mo</td>
                <td className="px-4 py-2 text-neutral-500">{loan.disbursementDate ?? "—"}</td>
              </tr>
            ))}
            {data.length === 0 && (
              <tr>
                <td className="px-4 py-3 text-neutral-500" colSpan={5}>
                  No loans.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </section>
  );
}

const STATE_COLORS: Record<string, string> = {
  CREATED: "bg-neutral-100 text-neutral-700",
  PENDING_DISBURSAL: "bg-amber-100 text-amber-800",
  ACTIVE: "bg-green-100 text-green-800",
  DELINQUENT: "bg-red-100 text-red-800",
  CLOSED: "bg-blue-100 text-blue-800",
  FORECLOSED: "bg-blue-100 text-blue-800",
  WRITTEN_OFF: "bg-neutral-200 text-neutral-800",
  CANCELLED: "bg-neutral-200 text-neutral-600",
};

export function StateBadge({ state }: { state: string }) {
  const color = STATE_COLORS[state] ?? "bg-neutral-100 text-neutral-700";
  return (
    <span className={`rounded px-2 py-0.5 text-xs font-medium ${color}`}>{state}</span>
  );
}
