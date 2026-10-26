import { useState } from "react";
import { useNotificationLog } from "../api/notifications";

const STATUSES = ["", "QUEUED", "SENT", "FAILED", "SUPPRESSED"];

const STATUS_COLORS: Record<string, string> = {
  QUEUED: "bg-amber-100 text-amber-800",
  SENT: "bg-green-100 text-green-800",
  FAILED: "bg-red-100 text-red-800",
  SUPPRESSED: "bg-neutral-200 text-neutral-700",
};

export function NotificationsPage() {
  const [status, setStatus] = useState("");
  const { data, isLoading, isError } = useNotificationLog(status || undefined);

  return (
    <section className="space-y-4">
      <h1 className="text-xl font-semibold">Notification log</h1>
      <label className="block text-sm text-neutral-600">
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

      {isLoading && <p className="text-sm text-neutral-500">Loading…</p>}
      {isError && <p className="text-sm text-red-700">Could not load the log.</p>}
      {data && (
        <table className="w-full overflow-hidden rounded border border-neutral-200 bg-white text-sm">
          <thead className="bg-neutral-100 text-left">
            <tr>
              <th className="px-4 py-2 font-medium">Created</th>
              <th className="px-4 py-2 font-medium">Template</th>
              <th className="px-4 py-2 font-medium">Channel</th>
              <th className="px-4 py-2 font-medium">Status</th>
              <th className="px-4 py-2 font-medium">Attempts</th>
              <th className="px-4 py-2 font-medium">Failure</th>
            </tr>
          </thead>
          <tbody>
            {data.map((n) => (
              <tr key={n.id} className="border-t border-neutral-100">
                <td className="px-4 py-2 text-neutral-500">{n.createdAt}</td>
                <td className="px-4 py-2 font-mono text-xs">{n.templateId}</td>
                <td className="px-4 py-2">{n.channel}</td>
                <td className="px-4 py-2">
                  <span
                    className={`rounded px-2 py-0.5 text-xs font-medium ${
                      STATUS_COLORS[n.status] ?? "bg-neutral-100 text-neutral-700"
                    }`}
                  >
                    {n.status}
                  </span>
                </td>
                <td className="px-4 py-2">{n.attempts}</td>
                <td className="px-4 py-2 text-neutral-500">{n.failureCode ?? "—"}</td>
              </tr>
            ))}
            {data.length === 0 && (
              <tr>
                <td className="px-4 py-3 text-neutral-500" colSpan={6}>
                  No notifications.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </section>
  );
}
