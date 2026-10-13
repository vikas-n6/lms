import { useState } from "react";
import { useConsents, useCustomer } from "../api/customers";

export function CustomerPage() {
  const [query, setQuery] = useState("");
  const [id, setId] = useState<string | undefined>(undefined);
  const { data: customer, isError } = useCustomer(id);
  const { data: consents } = useConsents(id);

  return (
    <section className="max-w-2xl">
      <h1 className="mb-4 text-xl font-semibold">Customer</h1>
      <div className="mb-4 flex gap-2">
        <input className="w-full max-w-md rounded border border-neutral-300 px-2 py-1 font-mono text-sm"
          placeholder="customer id" value={query} onChange={(e) => setQuery(e.target.value)} />
        <button className="rounded bg-neutral-900 px-3 py-1.5 text-sm text-white" onClick={() => setId(query.trim())}>Look up</button>
      </div>
      {isError && <p className="text-sm text-red-700">Customer not found.</p>}
      {customer && (
        <div className="rounded border border-neutral-200 bg-white p-4 text-sm">
          <div className="mb-1"><span className="text-neutral-500">Display name</span> {customer.displayName}</div>
          <div className="mb-1"><span className="text-neutral-500">Status</span> {customer.status}</div>
          <div className="mb-3"><span className="text-neutral-500">Registered</span> {new Date(customer.createdAt).toLocaleString()}</div>
          <div className="text-neutral-500">Consents</div>
          <div>{consents && consents.length > 0 ? consents.join(", ") : "none recorded"}</div>
          <p className="mt-3 text-xs text-neutral-400">
            KYC and bank-account status are published as events (no read endpoint in Phase 1);
            the application view reflects their effect on eligibility.
          </p>
        </div>
      )}
    </section>
  );
}
