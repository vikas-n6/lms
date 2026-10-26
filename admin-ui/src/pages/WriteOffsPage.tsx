import { useState } from "react";
import { Link } from "react-router-dom";
import {
  useApproveWriteOff,
  useRejectWriteOff,
  useWriteOffRequests,
} from "../api/writeOffs";

const STATUSES = ["", "REQUESTED", "APPROVED", "REJECTED"];

export function WriteOffsPage() {
  const [status, setStatus] = useState("REQUESTED");
  const { data, isLoading, isError } = useWriteOffRequests(status || undefined);
  const approve = useApproveWriteOff();
  const reject = useRejectWriteOff();

  // The acting admin; the two-person rule requires this to differ from requestedBy.
  const [actingUser, setActingUser] = useState("");

  return (
    <section className="space-y-4">
      <h1 className="text-xl font-semibold">Write-off requests</h1>

      <div className="rounded border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
        Two-person rule: a request must be approved by a <strong>different</strong> admin than the
        one who raised it, and only at DPD 180 or more. Set the acting admin below.
      </div>

      <div className="flex flex-wrap items-center gap-4 text-sm text-neutral-600">
        <label>
          Acting admin{" "}
          <input
            className="ml-1 rounded border border-neutral-300 px-2 py-1"
            value={actingUser}
            onChange={(e) => setActingUser(e.target.value)}
            placeholder="your admin id"
          />
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

      {(approve.isError || reject.isError) && (
        <p className="text-sm text-red-700">
          Action failed — the same admin cannot approve their own request, and DPD must be at least
          180.
        </p>
      )}
      {isLoading && <p className="text-sm text-neutral-500">Loading…</p>}
      {isError && <p className="text-sm text-red-700">Could not load requests.</p>}
      {data && (
        <table className="w-full overflow-hidden rounded border border-neutral-200 bg-white text-sm">
          <thead className="bg-neutral-100 text-left">
            <tr>
              <th className="px-4 py-2 font-medium">Loan</th>
              <th className="px-4 py-2 font-medium">Reason</th>
              <th className="px-4 py-2 font-medium">Requested by</th>
              <th className="px-4 py-2 font-medium">DPD</th>
              <th className="px-4 py-2 font-medium">Status</th>
              <th className="px-4 py-2 font-medium">Decided by</th>
              <th className="px-4 py-2 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            {data.map((r) => {
              const sameUser = actingUser !== "" && actingUser === r.requestedBy;
              return (
                <tr key={r.id} className="border-t border-neutral-100">
                  <td className="px-4 py-2 font-mono text-xs">
                    <Link className="text-blue-700 hover:underline" to={`/loans/${r.loanId}`}>
                      {r.loanNumber}
                    </Link>
                  </td>
                  <td className="px-4 py-2">{r.reason}</td>
                  <td className="px-4 py-2">{r.requestedBy}</td>
                  <td className="px-4 py-2">{r.dpdAtRequest}</td>
                  <td className="px-4 py-2">{r.status}</td>
                  <td className="px-4 py-2 text-neutral-500">{r.decidedBy ?? "—"}</td>
                  <td className="px-4 py-2">
                    {r.status === "REQUESTED" ? (
                      <div className="flex gap-2">
                        <button
                          className="rounded bg-neutral-900 px-2 py-1 text-xs text-white disabled:opacity-40"
                          disabled={!actingUser || sameUser || approve.isPending}
                          title={sameUser ? "You raised this request" : "Approve"}
                          onClick={() =>
                            approve.mutate({ requestId: r.id, actingUser })
                          }
                        >
                          Approve
                        </button>
                        <button
                          className="rounded border border-neutral-300 px-2 py-1 text-xs hover:bg-neutral-100 disabled:opacity-40"
                          disabled={!actingUser || reject.isPending}
                          onClick={() =>
                            reject.mutate({ requestId: r.id, actingUser, note: "rejected" })
                          }
                        >
                          Reject
                        </button>
                      </div>
                    ) : (
                      <span className="text-neutral-400">—</span>
                    )}
                  </td>
                </tr>
              );
            })}
            {data.length === 0 && (
              <tr>
                <td className="px-4 py-3 text-neutral-500" colSpan={7}>
                  No requests.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </section>
  );
}
