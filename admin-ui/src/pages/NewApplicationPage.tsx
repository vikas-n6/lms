import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useCreateApplication, type CreateApplication } from "../api/applications";

const INITIAL: CreateApplication = {
  customerId: "",
  productId: "MICRO-V1",
  requestedAmountMinor: 3000000,
  phone: "9000000001",
  dateOfBirth: "1992-01-01",
  declaredMonthlyIncomeMinor: 5000000,
  employmentType: "SALARIED",
  dataProcessingConsent: true,
  creditPullConsent: true,
  loanAgreementConsent: true,
};

export function NewApplicationPage() {
  const [form, setForm] = useState<CreateApplication>(INITIAL);
  const create = useCreateApplication();
  const navigate = useNavigate();

  function set<K extends keyof CreateApplication>(key: K, value: CreateApplication[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  function submit() {
    create.mutate(form, { onSuccess: (app) => app && navigate(`/applications/${app.id}`) });
  }

  const field = "mb-3 block text-sm";
  const input = "mt-1 w-full max-w-sm rounded border border-neutral-300 px-2 py-1";

  return (
    <section className="max-w-xl">
      <h1 className="mb-4 text-xl font-semibold">New application (back-office)</h1>
      <label className={field}>Customer id
        <input className={input} value={form.customerId} onChange={(e) => set("customerId", e.target.value)} />
      </label>
      <label className={field}>Phone (persona prefix drives the bureau sim)
        <input className={input} value={form.phone} onChange={(e) => set("phone", e.target.value)} />
      </label>
      <label className={field}>Date of birth
        <input type="date" className={input} value={form.dateOfBirth} onChange={(e) => set("dateOfBirth", e.target.value)} />
      </label>
      <label className={field}>Requested amount (minor units)
        <input type="number" className={input} value={form.requestedAmountMinor} onChange={(e) => set("requestedAmountMinor", Number(e.target.value))} />
      </label>
      <label className={field}>Declared monthly income (minor units)
        <input type="number" className={input} value={form.declaredMonthlyIncomeMinor} onChange={(e) => set("declaredMonthlyIncomeMinor", Number(e.target.value))} />
      </label>
      <label className={field}>Employment type
        <select className={input} value={form.employmentType} onChange={(e) => set("employmentType", e.target.value)}>
          <option>SALARIED</option><option>SELF_EMPLOYED</option><option>OTHER</option>
        </select>
      </label>
      <div className="mb-4 flex flex-col gap-1 text-sm">
        {(["dataProcessingConsent", "creditPullConsent", "loanAgreementConsent"] as const).map((c) => (
          <label key={c} className="flex items-center gap-2">
            <input type="checkbox" checked={form[c]} onChange={(e) => set(c, e.target.checked)} />
            {c}
          </label>
        ))}
      </div>
      {create.isError && <p className="mb-2 text-sm text-red-700">Create failed.</p>}
      <button className="rounded bg-neutral-900 px-4 py-2 text-sm text-white" onClick={submit} disabled={!form.customerId}>
        Create draft
      </button>
    </section>
  );
}
