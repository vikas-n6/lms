import { useState } from "react";
import { formatMoney } from "../lib/money";
import {
  type NewProductVersion,
  type ProductView,
  useCreateProductVersion,
  useCurrentProduct,
  useProductVersions,
} from "../api/products";

function toForm(p: ProductView): NewProductVersion {
  return {
    currency: p.currency,
    minPrincipalMinor: p.minPrincipalMinor,
    maxPrincipalMinor: p.maxPrincipalMinor,
    principalStepMinor: p.principalStepMinor,
    tenors: p.tenors,
    aprPercent: p.aprPercent,
    feePercent: p.feePercent,
    gstPercent: p.gstPercent,
    penalAprPercent: p.penalAprPercent,
    graceDays: p.graceDays,
    foreclosureChargePercent: p.foreclosureChargePercent,
    effectiveFrom: new Date().toISOString().slice(0, 10),
  };
}

export function ProductConfigPage() {
  const current = useCurrentProduct();
  const versions = useProductVersions();
  const create = useCreateProductVersion();
  const [form, setForm] = useState<NewProductVersion | null>(null);

  const draft = form ?? (current.data ? toForm(current.data) : null);

  function update<K extends keyof NewProductVersion>(key: K, value: NewProductVersion[K]) {
    if (draft) setForm({ ...draft, [key]: value });
  }

  return (
    <div className="space-y-8">
      <h1 className="text-xl font-semibold">Product configuration</h1>

      {current.data && (
        <section className="rounded-lg border border-neutral-200 bg-white p-4">
          <div className="text-sm font-medium">
            Current version: v{current.data.version} ({current.data.productId})
          </div>
          <div className="mt-2 grid grid-cols-2 gap-x-6 gap-y-1 text-sm text-neutral-600 md:grid-cols-3">
            <div>APR: {current.data.aprPercent}%</div>
            <div>Fee: {current.data.feePercent}%</div>
            <div>GST: {current.data.gstPercent}%</div>
            <div>Penal APR: {current.data.penalAprPercent}%</div>
            <div>Grace: {current.data.graceDays} days</div>
            <div>Tenors: {current.data.tenors.join(", ")}</div>
            <div>
              Principal:{" "}
              {formatMoney(current.data.minPrincipalMinor, current.data.currency)}–
              {formatMoney(current.data.maxPrincipalMinor, current.data.currency)}
            </div>
            <div>Effective from: {current.data.effectiveFrom}</div>
          </div>
        </section>
      )}

      <section className="space-y-3">
        <h2 className="text-sm font-medium text-neutral-500">All versions</h2>
        <div className="overflow-x-auto rounded-lg border border-neutral-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-neutral-50 text-left text-xs uppercase text-neutral-500">
              <tr>
                <th className="px-4 py-2">Version</th>
                <th className="px-4 py-2">APR</th>
                <th className="px-4 py-2">Fee</th>
                <th className="px-4 py-2">Penal</th>
                <th className="px-4 py-2">Grace</th>
                <th className="px-4 py-2">Effective from</th>
              </tr>
            </thead>
            <tbody>
              {(versions.data ?? []).map((v) => (
                <tr key={v.version} className="border-t border-neutral-100">
                  <td className="px-4 py-2 font-medium">v{v.version}</td>
                  <td className="px-4 py-2">{v.aprPercent}%</td>
                  <td className="px-4 py-2">{v.feePercent}%</td>
                  <td className="px-4 py-2">{v.penalAprPercent}%</td>
                  <td className="px-4 py-2">{v.graceDays}d</td>
                  <td className="px-4 py-2">{v.effectiveFrom}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="text-xs text-neutral-500">
          Existing loans keep the version they were created under; a new version only applies to
          loans created on or after its effective date.
        </p>
      </section>

      {draft && (
        <section className="space-y-3">
          <h2 className="text-sm font-medium text-neutral-500">Create a new version</h2>
          <form
            className="grid max-w-2xl grid-cols-2 gap-3 rounded-lg border border-neutral-200 bg-white p-4"
            onSubmit={(e) => {
              e.preventDefault();
              create.mutate(draft);
            }}
          >
            <Field label="APR %" value={draft.aprPercent} onChange={(v) => update("aprPercent", v)} />
            <Field label="Fee %" value={draft.feePercent} onChange={(v) => update("feePercent", v)} />
            <Field label="GST %" value={draft.gstPercent} onChange={(v) => update("gstPercent", v)} />
            <Field label="Penal APR %" value={draft.penalAprPercent} onChange={(v) => update("penalAprPercent", v)} />
            <Field
              label="Grace days"
              value={String(draft.graceDays)}
              onChange={(v) => update("graceDays", Number(v) || 0)}
            />
            <Field
              label="Foreclosure %"
              value={draft.foreclosureChargePercent}
              onChange={(v) => update("foreclosureChargePercent", v)}
            />
            <Field
              label="Min principal (minor)"
              value={String(draft.minPrincipalMinor)}
              onChange={(v) => update("minPrincipalMinor", Number(v) || 0)}
            />
            <Field
              label="Max principal (minor)"
              value={String(draft.maxPrincipalMinor)}
              onChange={(v) => update("maxPrincipalMinor", Number(v) || 0)}
            />
            <Field
              label="Effective from"
              value={draft.effectiveFrom}
              onChange={(v) => update("effectiveFrom", v)}
            />
            <div className="col-span-2 flex items-center gap-3">
              <button
                type="submit"
                disabled={create.isPending}
                className="rounded bg-neutral-900 px-4 py-2 text-sm text-white disabled:opacity-50"
              >
                {create.isPending ? "Creating…" : "Create version"}
              </button>
              {create.isSuccess && (
                <span className="text-sm text-emerald-600">Created v{create.data?.version}.</span>
              )}
              {create.isError && <span className="text-sm text-red-600">Failed to create.</span>}
            </div>
          </form>
        </section>
      )}
    </div>
  );
}

function Field({
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
