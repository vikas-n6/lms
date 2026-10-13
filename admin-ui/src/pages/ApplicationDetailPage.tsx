import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useApplication, useApplicationAction } from "../api/applications";
import { formatMoney } from "../lib/money";

const LIFECYCLE = ["DRAFT", "SUBMITTED", "DECISIONED", "OFFERED", "ACCEPTED"];

export function ApplicationDetailPage() {
  const { id = "" } = useParams();
  const { data: app, isLoading } = useApplication(id);
  const actions = useApplicationAction(id);
  const [tenor, setTenor] = useState(12);
  const [otp, setOtp] = useState("");

  if (isLoading || !app) return <p className="text-sm text-neutral-500">Loading…</p>;
  const reached = LIFECYCLE.indexOf(app.status);

  return (
    <section className="max-w-3xl">
      <Link to="/applications" className="text-sm text-blue-700 hover:underline">← Applications</Link>
      <h1 className="mt-2 text-xl font-semibold">Application {app.id}</h1>
      <p className="mb-4 text-sm text-neutral-600">
        Customer <span className="font-mono">{app.customerId}</span> · requested{" "}
        {formatMoney(app.requestedAmountMinor, app.currency)}
      </p>

      <h2 className="mb-2 text-sm font-semibold text-neutral-700">Lifecycle</h2>
      <ol className="mb-6 flex flex-wrap gap-2 text-xs">
        {LIFECYCLE.map((s, i) => (
          <li key={s} className={`rounded px-2 py-1 ${i <= reached ? "bg-neutral-900 text-white" : "bg-neutral-200 text-neutral-600"}`}>{s}</li>
        ))}
        {["REJECTED", "EXPIRED", "WITHDRAWN"].includes(app.status) && (
          <li className="rounded bg-red-100 px-2 py-1 text-red-800">{app.status}</li>
        )}
      </ol>

      {app.offerOptions.length > 0 && (
        <>
          <h2 className="mb-2 text-sm font-semibold text-neutral-700">
            Offer options {app.offerExpiresAt && <span className="font-normal text-neutral-500">(expires {new Date(app.offerExpiresAt).toLocaleString()})</span>}
          </h2>
          <table className="mb-4 w-full overflow-hidden rounded border border-neutral-200 bg-white text-sm">
            <thead className="bg-neutral-100 text-left">
              <tr><th className="px-3 py-2">Tenor</th><th className="px-3 py-2">EMI</th><th className="px-3 py-2">Fee</th><th className="px-3 py-2">GST</th><th className="px-3 py-2">Net disbursal</th><th className="px-3 py-2">Eff. APR</th></tr>
            </thead>
            <tbody>
              {app.offerOptions.map((o) => (
                <tr key={o.tenorMonths} className="border-t border-neutral-100">
                  <td className="px-3 py-2">{o.tenorMonths}m</td>
                  <td className="px-3 py-2">{formatMoney(o.emiMinor, app.currency)}</td>
                  <td className="px-3 py-2">{formatMoney(o.feeMinor, app.currency)}</td>
                  <td className="px-3 py-2">{formatMoney(o.gstMinor, app.currency)}</td>
                  <td className="px-3 py-2">{formatMoney(o.netDisbursalMinor, app.currency)}</td>
                  <td className="px-3 py-2">{o.effectiveAnnualRatePercent}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {app.acceptedTerms && (
        <div className="mb-4 rounded border border-green-200 bg-green-50 p-3 text-sm">
          Accepted {app.acceptedTerms.tenorMonths}m · EMI {formatMoney(app.acceptedTerms.emiMinor, app.currency)} ·
          net {formatMoney(app.acceptedTerms.netDisbursalMinor, app.currency)} · APR {app.acceptedTerms.effectiveAnnualRatePercent}%
        </div>
      )}

      <div className="flex flex-wrap items-end gap-3">
        {app.status === "DRAFT" && (
          <button className="rounded bg-neutral-900 px-3 py-1.5 text-sm text-white" onClick={() => actions.submit.mutate()}>Submit</button>
        )}
        {app.status === "OFFERED" && (
          <>
            <button className="rounded border border-neutral-300 px-3 py-1.5 text-sm" onClick={() => actions.requestEsign.mutate()}>Send e-sign OTP</button>
            <label className="text-sm">Tenor
              <select className="ml-1 rounded border border-neutral-300 px-2 py-1" value={tenor} onChange={(e) => setTenor(Number(e.target.value))}>
                {app.offerOptions.map((o) => <option key={o.tenorMonths} value={o.tenorMonths}>{o.tenorMonths}m</option>)}
              </select>
            </label>
            <label className="text-sm">OTP
              <input className="ml-1 w-28 rounded border border-neutral-300 px-2 py-1" value={otp} onChange={(e) => setOtp(e.target.value)} />
            </label>
            <button className="rounded bg-neutral-900 px-3 py-1.5 text-sm text-white" onClick={() => actions.accept.mutate({ tenorMonths: tenor, otpCode: otp })}>Accept</button>
          </>
        )}
        {["DRAFT", "SUBMITTED", "DECISIONED", "OFFERED"].includes(app.status) && (
          <button className="rounded border border-red-300 px-3 py-1.5 text-sm text-red-700" onClick={() => actions.withdraw.mutate()}>Withdraw</button>
        )}
      </div>
    </section>
  );
}
