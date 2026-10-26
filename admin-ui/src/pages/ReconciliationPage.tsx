import { useState } from "react";
import {
  useReconItems,
  useReconRuns,
  useResolveReconItem,
  type ReconRun,
} from "../api/reconciliation";
import { formatMoney } from "../lib/money";

export function ReconciliationPage() {
  const { data: runs, isLoading, isError } = useReconRuns();
  const [selected, setSelected] = useState<string | undefined>(undefined);
  const { data: items } = useReconItems(selected);
  const resolve = useResolveReconItem();

  return (
    <section className="space-y-5">
      <h1 className="text-xl font-semibold">Reconciliation results</h1>

      {isLoading && <p className="text-sm text-neutral-500">Loading…</p>}
      {isError && <p className="text-sm text-red-700">Could not load runs.</p>}
      {runs && (
        <table className="w-full overflow-hidden rounded border border-neutral-200 bg-white text-sm">
          <thead className="bg-neutral-100 text-left">
            <tr>
              <th className="px-4 py-2 font-medium">Date</th>
              <th className="px-4 py-2 font-medium">Matched</th>
              <th className="px-4 py-2 font-medium">Missing (ledger)</th>
              <th className="px-4 py-2 font-medium">Missing (bank)</th>
              <th className="px-4 py-2 font-medium">Amount mismatch</th>
              <th className="px-4 py-2 font-medium">Duplicate</th>
              <th className="px-4 py-2 font-medium" />
            </tr>
          </thead>
          <tbody>
            {runs.map((r: ReconRun) => (
              <tr key={r.id} className="border-t border-neutral-100">
                <td className="px-4 py-2">{r.reconDate}</td>
                <td className="px-4 py-2">{r.matched}</td>
                <td className={cell(r.missingInLedger)}>{r.missingInLedger}</td>
                <td className={cell(r.missingInBank)}>{r.missingInBank}</td>
                <td className={cell(r.amountMismatch)}>{r.amountMismatch}</td>
                <td className={cell(r.duplicate)}>{r.duplicate}</td>
                <td className="px-4 py-2">
                  <button
                    className="rounded border border-neutral-300 px-2 py-1 text-xs hover:bg-neutral-100"
                    onClick={() => setSelected(r.id)}
                  >
                    Items
                  </button>
                </td>
              </tr>
            ))}
            {runs.length === 0 && (
              <tr>
                <td className="px-4 py-3 text-neutral-500" colSpan={7}>
                  No reconciliation runs.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}

      {selected && items && (
        <div>
          <h2 className="mb-2 text-sm font-semibold text-neutral-700">Items for run {selected}</h2>
          <table className="w-full overflow-hidden rounded border border-neutral-200 bg-white text-sm">
            <thead className="bg-neutral-100 text-left">
              <tr>
                <th className="px-4 py-2 font-medium">Reference</th>
                <th className="px-4 py-2 font-medium">Classification</th>
                <th className="px-4 py-2 font-medium">Bank/rail</th>
                <th className="px-4 py-2 font-medium">Ledger</th>
                <th className="px-4 py-2 font-medium">Resolved</th>
                <th className="px-4 py-2 font-medium" />
              </tr>
            </thead>
            <tbody>
              {items.map((s) => (
                <tr key={s.id} className="border-t border-neutral-100">
                  <td className="px-4 py-2 font-mono text-xs">{s.item.reference}</td>
                  <td className="px-4 py-2">{s.item.classification}</td>
                  <td className="px-4 py-2">
                    {s.item.externalAmount
                      ? formatMoney(
                          s.item.externalAmount.minorUnits,
                          s.item.externalAmount.currency,
                        )
                      : "—"}
                  </td>
                  <td className="px-4 py-2">
                    {s.item.ledgerAmount
                      ? formatMoney(s.item.ledgerAmount.minorUnits, s.item.ledgerAmount.currency)
                      : "—"}
                  </td>
                  <td className="px-4 py-2">{s.resolved ? "yes" : "no"}</td>
                  <td className="px-4 py-2">
                    {!s.resolved && s.item.classification !== "MATCHED" && (
                      <button
                        className="rounded border border-neutral-300 px-2 py-1 text-xs hover:bg-neutral-100 disabled:opacity-40"
                        disabled={resolve.isPending}
                        onClick={() =>
                          resolve.mutate({ itemId: s.id, note: "reviewed and resolved" })
                        }
                      >
                        Resolve
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function cell(count: number): string {
  return `px-4 py-2 ${count > 0 ? "font-semibold text-red-700" : ""}`;
}
