import { useState } from "react";
import { Link } from "react-router-dom";
import { useCases, type CollectionCase } from "../api/collections";

const BUCKETS = ["", "DPD_1_30", "DPD_31_60", "DPD_61_90", "NPA"];
const STATUSES = [
  "",
  "OPEN",
  "PROMISE_TO_PAY",
  "ESCALATED",
  "WRITE_OFF_RECOMMENDED",
  "RESOLVED",
  "WRITTEN_OFF",
];

export function CollectionsPage() {
  const [bucket, setBucket] = useState("");
  const [status, setStatus] = useState("");
  const { data, isLoading, isError } = useCases({
    bucket: bucket || undefined,
    status: status || undefined,
  });

  const summary = bucketSummary(data ?? []);

  return (
    <section>
      <h1 className="mb-4 text-xl font-semibold">Collections queue</h1>

      <div className="mb-5 grid grid-cols-2 gap-3 sm:grid-cols-5">
        {BUCKETS.filter((b) => b).map((b) => (
          <div key={b} className="rounded border border-neutral-200 bg-white p-3">
            <div className="text-xs text-neutral-500">{b}</div>
            <div className="text-2xl font-semibold">{summary[b] ?? 0}</div>
          </div>
        ))}
      </div>

      <div className="mb-3 flex flex-wrap gap-4 text-sm text-neutral-600">
        <label>
          Bucket{" "}
          <select
            className="ml-1 rounded border border-neutral-300 px-2 py-1"
            value={bucket}
            onChange={(e) => setBucket(e.target.value)}
          >
            {BUCKETS.map((b) => (
              <option key={b} value={b}>
                {b || "all"}
              </option>
            ))}
          </select>
        </label>
        <label>
          Status{" "}
          <select
            className="ml-1 rounded border border-neutral-300 px-2 py-1"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
          >
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s || "all"}
              </option>
            ))}
          </select>
        </label>
      </div>

      {isLoading && <p className="text-sm text-neutral-500">Loading…</p>}
      {isError && <p className="text-sm text-red-700">Could not load cases.</p>}
      {data && (
        <table className="w-full overflow-hidden rounded border border-neutral-200 bg-white text-sm">
          <thead className="bg-neutral-100 text-left">
            <tr>
              <th className="px-4 py-2 font-medium">Loan</th>
              <th className="px-4 py-2 font-medium">Bucket</th>
              <th className="px-4 py-2 font-medium">DPD</th>
              <th className="px-4 py-2 font-medium">Status</th>
              <th className="px-4 py-2 font-medium">Agent</th>
            </tr>
          </thead>
          <tbody>
            {data.map((c) => (
              <tr key={c.id} className="border-t border-neutral-100">
                <td className="px-4 py-2 font-mono text-xs">
                  <Link className="text-blue-700 hover:underline" to={`/collections/${c.id}`}>
                    {c.loanNumber}
                  </Link>
                </td>
                <td className="px-4 py-2">{c.bucket}</td>
                <td className="px-4 py-2">{c.daysPastDue}</td>
                <td className="px-4 py-2">
                  <CaseStatusBadge status={c.status} />
                </td>
                <td className="px-4 py-2 text-neutral-500">{c.assignedAgent ?? "—"}</td>
              </tr>
            ))}
            {data.length === 0 && (
              <tr>
                <td className="px-4 py-3 text-neutral-500" colSpan={5}>
                  No cases.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </section>
  );
}

function bucketSummary(cases: CollectionCase[]): Record<string, number> {
  const counts: Record<string, number> = {};
  for (const c of cases) counts[c.bucket] = (counts[c.bucket] ?? 0) + 1;
  return counts;
}

const STATUS_COLORS: Record<string, string> = {
  OPEN: "bg-amber-100 text-amber-800",
  PROMISE_TO_PAY: "bg-blue-100 text-blue-800",
  ESCALATED: "bg-red-100 text-red-800",
  WRITE_OFF_RECOMMENDED: "bg-red-200 text-red-900",
  RESOLVED: "bg-green-100 text-green-800",
  WRITTEN_OFF: "bg-neutral-200 text-neutral-800",
};

export function CaseStatusBadge({ status }: { status: string }) {
  const color = STATUS_COLORS[status] ?? "bg-neutral-100 text-neutral-700";
  return <span className={`rounded px-2 py-0.5 text-xs font-medium ${color}`}>{status}</span>;
}
