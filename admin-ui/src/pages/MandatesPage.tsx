import { useState } from "react";
import { useMandate, useRegisterMandate } from "../api/mandates";

/** Mandates (phase prompt S7): register an auto-debit mandate and show its status for a loan. */
export function MandatesPage() {
  const [loanId, setLoanId] = useState("");
  const [customerId, setCustomerId] = useState("");
  const [lookup, setLookup] = useState<string | undefined>(undefined);

  const register = useRegisterMandate();
  const mandate = useMandate(lookup);

  return (
    <section className="max-w-xl">
      <h1 className="mb-4 text-xl font-semibold">Mandates</h1>

      <div className="space-y-2 rounded border border-neutral-200 bg-white p-4 text-sm">
        <label className="block">
          <span className="mb-1 block text-xs text-neutral-600">Loan id</span>
          <input className="input" value={loanId} onChange={(e) => setLoanId(e.target.value)} />
        </label>
        <label className="block">
          <span className="mb-1 block text-xs text-neutral-600">Customer id</span>
          <input
            className="input"
            value={customerId}
            onChange={(e) => setCustomerId(e.target.value)}
          />
        </label>
        <div className="flex gap-2 pt-1">
          <button
            className="rounded bg-blue-600 px-3 py-1 text-white disabled:opacity-50"
            disabled={!loanId || !customerId || register.isPending}
            onClick={() =>
              register.mutate({ loanId, customerId }, { onSuccess: () => setLookup(loanId) })
            }
          >
            Register mandate
          </button>
          <button
            className="rounded border border-neutral-300 px-3 py-1 disabled:opacity-50"
            disabled={!loanId}
            onClick={() => setLookup(loanId)}
          >
            Check status
          </button>
        </div>
      </div>

      {lookup && (
        <div className="mt-4 rounded border border-neutral-200 bg-white p-4 text-sm">
          {mandate.isLoading && <p className="text-neutral-500">Loading…</p>}
          {mandate.isError && <p className="text-neutral-500">No active mandate for this loan.</p>}
          {mandate.data && (
            <p>
              Mandate <span className="font-mono text-xs">{mandate.data.id}</span> —{" "}
              <span className="font-medium">{mandate.data.status}</span>
            </p>
          )}
        </div>
      )}
    </section>
  );
}
