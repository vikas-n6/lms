import { useState } from "react";
import { useCreatePaymentLink, useManualReceipt } from "../api/repayments";

/** Repayments (phase prompt S7): create a payment link and the manual-collection dialog. */
export function RepaymentsPage() {
  const [loanId, setLoanId] = useState("");
  const [amount, setAmount] = useState("0");
  const [currency, setCurrency] = useState("INR");
  const [bankRef, setBankRef] = useState("");
  const [foreclosure, setForeclosure] = useState(false);
  const [quoteId, setQuoteId] = useState("");

  const link = useCreatePaymentLink();
  const manual = useManualReceipt();

  const minor = Number.parseInt(amount, 10) || 0;
  const purpose = foreclosure ? "FORECLOSURE" : undefined;
  const quote = foreclosure && quoteId ? quoteId : undefined;

  return (
    <section className="max-w-xl">
      <h1 className="mb-4 text-xl font-semibold">Repayments</h1>

      <div className="space-y-2 rounded border border-neutral-200 bg-white p-4 text-sm">
        <Field label="Loan id">
          <input className="input" value={loanId} onChange={(e) => setLoanId(e.target.value)} />
        </Field>
        <Field label="Amount (minor units)">
          <input className="input" value={amount} onChange={(e) => setAmount(e.target.value)} />
        </Field>
        <Field label="Currency">
          <input className="input" value={currency} onChange={(e) => setCurrency(e.target.value)} />
        </Field>
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={foreclosure}
            onChange={(e) => setForeclosure(e.target.checked)}
          />
          Foreclosure
        </label>
        {foreclosure && (
          <Field label="Foreclosure quote id">
            <input className="input" value={quoteId} onChange={(e) => setQuoteId(e.target.value)} />
          </Field>
        )}

        <div className="flex gap-2 pt-2">
          <button
            className="rounded bg-blue-600 px-3 py-1 text-white disabled:opacity-50"
            disabled={!loanId || link.isPending}
            onClick={() =>
              link.mutate({ loanId, amountMinor: minor, currency, purpose, foreclosureQuoteId: quote })
            }
          >
            Create payment link
          </button>
        </div>
        {link.data && (
          <p className="text-xs">
            Link:{" "}
            <a className="text-blue-700 hover:underline" href={link.data.url}>
              {link.data.linkId}
            </a>
          </p>
        )}
      </div>

      <div className="mt-4 space-y-2 rounded border border-neutral-200 bg-white p-4 text-sm">
        <h2 className="font-semibold">Manual collection</h2>
        <Field label="Bank reference">
          <input className="input" value={bankRef} onChange={(e) => setBankRef(e.target.value)} />
        </Field>
        <button
          className="rounded bg-blue-600 px-3 py-1 text-white disabled:opacity-50"
          disabled={!loanId || !bankRef || manual.isPending}
          onClick={() =>
            manual.mutate({
              loanId,
              amountMinor: minor,
              currency,
              bankReference: bankRef,
              purpose,
              foreclosureQuoteId: quote,
            })
          }
        >
          Record manual receipt
        </button>
        {manual.data && (
          <p className="text-xs text-neutral-600">
            {manual.data.status}
            {manual.data.repaymentId ? ` (${manual.data.repaymentId})` : ""}
          </p>
        )}
      </div>
    </section>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-xs text-neutral-600">{label}</span>
      {children}
    </label>
  );
}
