import { useState } from "react";
import { Link } from "react-router-dom";
import { useApplications } from "../api/applications";
import { formatMoney } from "../lib/money";

const STATUSES = ["", "DRAFT", "SUBMITTED", "DECISIONED", "OFFERED", "ACCEPTED", "REJECTED", "EXPIRED", "WITHDRAWN"];

export function ApplicationsPage() {
  const [status, setStatus] = useState("");
  const { data, isLoading, isError } = useApplications(status || undefined);

  return (
    <section>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-semibold">Applications</h1>
        <Link to="/applications/new" className="rounded bg-neutral-900 px-3 py-1.5 text-sm text-white">
          New application
        </Link>
      </div>
      <label className="mb-3 block text-sm text-neutral-600">
        Filter status{" "}
        <select className="ml-2 rounded border border-neutral-300 px-2 py-1" value={status}
          onChange={(e) => setStatus(e.target.value)}>
          {STATUSES.map((s) => <option key={s} value={s}>{s || "all"}</option>)}
        </select>
      </label>
      {isLoading && <p className="text-sm text-neutral-500">Loading…</p>}
      {isError && <p className="text-sm text-red-700">Could not load applications.</p>}
      {data && (
        <table className="w-full overflow-hidden rounded border border-neutral-200 bg-white text-sm">
          <thead className="bg-neutral-100 text-left">
            <tr>
              <th className="px-4 py-2 font-medium">Application</th>
              <th className="px-4 py-2 font-medium">Status</th>
              <th className="px-4 py-2 font-medium">Requested</th>
              <th className="px-4 py-2 font-medium">Updated</th>
            </tr>
          </thead>
          <tbody>
            {data.map((a) => (
              <tr key={a.id} className="border-t border-neutral-100">
                <td className="px-4 py-2 font-mono text-xs">
                  <Link className="text-blue-700 hover:underline" to={`/applications/${a.id}`}>{a.id}</Link>
                </td>
                <td className="px-4 py-2">{a.status}</td>
                <td className="px-4 py-2">{formatMoney(a.requestedAmountMinor, a.currency)}</td>
                <td className="px-4 py-2 text-neutral-500">{new Date(a.updatedAt).toLocaleString()}</td>
              </tr>
            ))}
            {data.length === 0 && (
              <tr><td className="px-4 py-3 text-neutral-500" colSpan={4}>No applications.</td></tr>
            )}
          </tbody>
        </table>
      )}
    </section>
  );
}
