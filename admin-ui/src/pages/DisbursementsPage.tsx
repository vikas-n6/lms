import { useState } from "react";
import {
  useDisbursement,
  useDisbursementAction,
  useDisbursements,
} from "../api/disbursements";
import { formatMoney } from "../lib/money";

const STATUS_COLORS: Record<string, string> = {
  INITIATED: "bg-amber-100 text-amber-800",
  SUCCEEDED: "bg-green-100 text-green-800",
  FAILED: "bg-red-100 text-red-800",
  MANUAL_REVIEW: "bg-purple-100 text-purple-800",
};

function StatusBadge({ status }: { status: string }) {
  const color = STATUS_COLORS[status] ?? "bg-neutral-100 text-neutral-700";
  return <span className={`rounded px-2 py-0.5 text-xs font-medium ${color}`}>{status}</span>;
}

/** Disbursement monitor (phase prompt S7): list, status, attempts, Temporal link, retry/cancel. */
export function DisbursementsPage() {
  const { data, isLoading, isError } = useDisbursements();
  const [selected, setSelected] = useState<string | undefined>(undefined);

  return (
    <section>
      <h1 className="mb-4 text-xl font-semibold">Disbursement monitor</h1>
      {isLoading && <p className="text-sm text-neutral-500">Loading…</p>}
      {isError && <p className="text-sm text-red-700">Could not load disbursements.</p>}
      {data && (
        <table className="w-full overflow-hidden rounded border border-neutral-200 bg-white text-sm">
          <thead className="bg-neutral-100 text-left">
            <tr>
              <th className="px-4 py-2 font-medium">Loan</th>
              <th className="px-4 py-2 font-medium">Status</th>
              <th className="px-4 py-2 font-medium">Net</th>
              <th className="px-4 py-2 font-medium">Attempts</th>
              <th className="px-4 py-2 font-medium">UTR</th>
              <th className="px-4 py-2 font-medium"></th>
            </tr>
          </thead>
          <tbody>
            {data.map((d) => (
              <tr key={d.id} className="border-t border-neutral-100">
                <td className="px-4 py-2 font-mono text-xs">{d.loanNumber}</td>
                <td className="px-4 py-2">
                  <StatusBadge status={d.status} />
                </td>
                <td className="px-4 py-2">{formatMoney(d.netMinorUnits, d.currency)}</td>
                <td className="px-4 py-2">{d.attempts}</td>
                <td className="px-4 py-2 font-mono text-xs text-neutral-500">{d.utr ?? "—"}</td>
                <td className="px-4 py-2">
                  <button
                    className="text-blue-700 hover:underline"
                    onClick={() => setSelected(d.id)}
                  >
                    detail
                  </button>
                </td>
              </tr>
            ))}
            {data.length === 0 && (
              <tr>
                <td className="px-4 py-3 text-neutral-500" colSpan={6}>
                  No disbursements.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}
      {selected && <DisbursementDetailCard id={selected} onClose={() => setSelected(undefined)} />}
    </section>
  );
}

function DisbursementDetailCard({ id, onClose }: { id: string; onClose: () => void }) {
  const { data } = useDisbursement(id);
  const { retry, cancel } = useDisbursementAction(id);
  if (!data) return null;
  const d = data.disbursement;
  const inReview = d.status === "MANUAL_REVIEW";
  return (
    <div className="mt-4 rounded border border-neutral-200 bg-white p-4 text-sm">
      <div className="mb-2 flex items-center justify-between">
        <h2 className="font-semibold">
          {d.loanNumber} <StatusBadge status={d.status} />
        </h2>
        <button className="text-neutral-500 hover:underline" onClick={onClose}>
          close
        </button>
      </div>
      <p className="mb-2">
        <a
          className="text-blue-700 hover:underline"
          href={data.temporalUiLink}
          target="_blank"
          rel="noreferrer"
        >
          Open workflow in Temporal UI →
        </a>
      </p>
      {d.failureReason && <p className="mb-2 text-red-700">Failure: {d.failureReason}</p>}
      <table className="w-full text-xs">
        <thead className="text-left text-neutral-500">
          <tr>
            <th className="py-1">#</th>
            <th className="py-1">Idempotency key</th>
            <th className="py-1">Status</th>
            <th className="py-1">Failure</th>
          </tr>
        </thead>
        <tbody>
          {data.attempts.map((a) => (
            <tr key={a.id} className="border-t border-neutral-100">
              <td className="py-1">{a.attemptNumber}</td>
              <td className="py-1 font-mono">{a.idempotencyKey}</td>
              <td className="py-1">{a.status}</td>
              <td className="py-1 text-neutral-500">{a.failureCode ?? "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {inReview && (
        <div className="mt-3 flex gap-2">
          <button
            className="rounded bg-blue-600 px-3 py-1 text-white disabled:opacity-50"
            disabled={retry.isPending}
            onClick={() => retry.mutate()}
          >
            Retry
          </button>
          <button
            className="rounded border border-red-300 px-3 py-1 text-red-700 disabled:opacity-50"
            disabled={cancel.isPending}
            onClick={() => cancel.mutate()}
          >
            Cancel
          </button>
        </div>
      )}
    </div>
  );
}
