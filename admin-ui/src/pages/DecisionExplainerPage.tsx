import { useState } from "react";
import { useParams } from "react-router-dom";
import { useExplain, type Explain } from "../api/decisions";
import { formatMoney } from "../lib/money";

function statusClass(s: string) {
  const b = "rounded px-2 py-0.5 text-xs font-medium ";
  if (s === "PASS") return b + "bg-green-100 text-green-800";
  if (s === "FAIL") return b + "bg-red-100 text-red-800";
  return b + "bg-amber-100 text-amber-800";
}

export function ExplainView({ d }: { d: Explain }) {
  return (
    <div className="max-w-3xl">
      <div className="mb-3 flex items-center gap-3">
        <span className="text-lg font-semibold">{d.outcome}</span>
        {d.manualOverride && <span className="rounded bg-blue-100 px-2 py-0.5 text-xs text-blue-800">manual override</span>}
        <span className="text-sm text-neutral-500">rule set {d.ruleSetVersion}</span>
      </div>
      <p className="mb-3 text-sm text-neutral-600">
        Application <span className="font-mono">{d.applicationId}</span>
        {d.offeredLimitMinor != null && <> · offered limit {formatMoney(d.offeredLimitMinor, d.currency)}</>}
      </p>
      <div className="mb-4 rounded border border-neutral-200 bg-white p-3 text-sm">
        <div className="font-medium">Bureau snapshot</div>
        {d.bureau.available
          ? <div className="text-neutral-700">score {d.bureau.score} · {d.bureau.activeAccounts} accounts · {d.bureau.overdue ? "overdue" : "no overdue"}</div>
          : <div className="text-amber-700">bureau unavailable</div>}
      </div>
      {d.reasonCodes.length > 0 && (
        <p className="mb-3 text-sm">Reason codes: {d.reasonCodes.map((c) => <code key={c} className="mr-1 rounded bg-neutral-100 px-1">{c}</code>)}</p>
      )}
      <table className="w-full overflow-hidden rounded border border-neutral-200 bg-white text-sm">
        <thead className="bg-neutral-100 text-left"><tr><th className="px-3 py-2">Rule</th><th className="px-3 py-2">Result</th><th className="px-3 py-2">Reason</th><th className="px-3 py-2">Detail</th></tr></thead>
        <tbody>
          {d.rules.map((r) => (
            <tr key={r.rule} className="border-t border-neutral-100">
              <td className="px-3 py-2 font-medium">{r.rule}</td>
              <td className="px-3 py-2"><span className={statusClass(r.status)}>{r.status}</span></td>
              <td className="px-3 py-2">{r.reasonCode ?? "—"}</td>
              <td className="px-3 py-2 text-neutral-600">{r.detail}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {d.reviewNote && <p className="mt-3 text-sm text-neutral-600">Review note: {d.reviewNote}</p>}
    </div>
  );
}

export function DecisionExplainerPage() {
  const { id: routeId } = useParams();
  const [id, setId] = useState(routeId ?? "");
  const { data, isError } = useExplain(id || undefined);

  return (
    <section>
      <h1 className="mb-4 text-xl font-semibold">Decision explainer</h1>
      <input className="mb-4 w-full max-w-md rounded border border-neutral-300 px-2 py-1 font-mono text-sm"
        placeholder="decision id" value={id} onChange={(e) => setId(e.target.value)} />
      {isError && <p className="text-sm text-red-700">Decision not found.</p>}
      {data && <ExplainView d={data} />}
    </section>
  );
}
