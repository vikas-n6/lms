import { useState } from "react";
import { type AuditFilters, useAuditLog } from "../api/reporting";

export function AuditLogPage() {
  const [draft, setDraft] = useState<AuditFilters>({});
  const [applied, setApplied] = useState<AuditFilters>({});
  const audit = useAuditLog(applied);

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold">Audit log</h1>

      <form
        className="flex flex-wrap items-end gap-3 rounded-lg border border-neutral-200 bg-white p-4"
        onSubmit={(e) => {
          e.preventDefault();
          setApplied(draft);
        }}
      >
        <Filter label="Actor" value={draft.actor ?? ""} onChange={(v) => setDraft({ ...draft, actor: v || undefined })} />
        <Filter
          label="Action"
          value={draft.action ?? ""}
          onChange={(v) => setDraft({ ...draft, action: v || undefined })}
        />
        <Filter
          label="Target type"
          value={draft.targetType ?? ""}
          onChange={(v) => setDraft({ ...draft, targetType: v || undefined })}
        />
        <button type="submit" className="rounded bg-neutral-900 px-4 py-2 text-sm text-white">
          Filter
        </button>
      </form>

      {audit.isError && <p className="text-sm text-red-600">Failed to load audit log.</p>}

      <div className="overflow-x-auto rounded-lg border border-neutral-200 bg-white">
        <table className="w-full text-sm">
          <thead className="bg-neutral-50 text-left text-xs uppercase text-neutral-500">
            <tr>
              <th className="px-4 py-2">When</th>
              <th className="px-4 py-2">Actor</th>
              <th className="px-4 py-2">Action</th>
              <th className="px-4 py-2">Target</th>
              <th className="px-4 py-2">Before → After</th>
              <th className="px-4 py-2">Outcome</th>
            </tr>
          </thead>
          <tbody>
            {(audit.data ?? []).map((r) => (
              <tr key={r.eventId} className="border-t border-neutral-100 align-top">
                <td className="px-4 py-2 whitespace-nowrap text-neutral-500">
                  {new Date(r.recordedAt).toLocaleString()}
                </td>
                <td className="px-4 py-2">{r.actorId}</td>
                <td className="px-4 py-2 font-medium">{r.action}</td>
                <td className="px-4 py-2 text-neutral-600">
                  {r.targetType}/{r.targetId}
                </td>
                <td className="px-4 py-2 text-neutral-600">
                  {r.beforeSummary ?? "—"} → {r.afterSummary ?? "—"}
                </td>
                <td className="px-4 py-2">
                  <span
                    className={
                      r.outcome === "SUCCESS"
                        ? "text-emerald-600"
                        : r.outcome === "DENIED"
                          ? "text-orange-600"
                          : "text-red-600"
                    }
                  >
                    {r.outcome}
                  </span>
                </td>
              </tr>
            ))}
            {(audit.data ?? []).length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-neutral-400" colSpan={6}>
                  No audit entries.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function Filter({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <label className="flex flex-col gap-1 text-sm">
      <span className="text-neutral-600">{label}</span>
      <input
        className="rounded border border-neutral-300 px-2 py-1"
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  );
}
