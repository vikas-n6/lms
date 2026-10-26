import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  useAssignAgent,
  useCase,
  useEscalateCase,
  useRecordPromise,
  useResolveCase,
} from "../api/collections";
import { CaseStatusBadge } from "./CollectionsPage";

export function CaseDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { data, isLoading, isError } = useCase(id);
  const assign = useAssignAgent(id ?? "");
  const promise = useRecordPromise(id ?? "");
  const escalate = useEscalateCase(id ?? "");
  const resolve = useResolveCase(id ?? "");

  const [agent, setAgent] = useState("");
  const [promiseDate, setPromiseDate] = useState("");
  const [promiseAmount, setPromiseAmount] = useState("");

  if (isLoading) return <p className="text-sm text-neutral-500">Loading…</p>;
  if (isError || !data) return <p className="text-sm text-red-700">Could not load the case.</p>;

  const c = data.theCase;

  return (
    <section className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">
          Case for{" "}
          <Link className="text-blue-700 hover:underline" to={`/loans/${c.loanId}`}>
            {c.loanNumber}
          </Link>
        </h1>
        <CaseStatusBadge status={c.status} />
      </div>

      <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
        <Fact label="Bucket" value={c.bucket} />
        <Fact label="DPD" value={String(c.daysPastDue)} />
        <Fact label="Agent" value={c.assignedAgent ?? "—"} />
        <Fact label="Plan version" value={String(c.planVersion)} />
        <Fact label="Promise date" value={c.promiseDate ?? "—"} />
      </div>

      <div className="flex flex-wrap items-end gap-6 rounded border border-neutral-200 bg-white p-4">
        <div>
          <div className="mb-1 text-xs font-medium text-neutral-600">Assign agent</div>
          <div className="flex gap-2">
            <input
              className="rounded border border-neutral-300 px-2 py-1 text-sm"
              value={agent}
              onChange={(e) => setAgent(e.target.value)}
              placeholder="agent id"
            />
            <button
              className="rounded bg-neutral-900 px-3 py-1 text-sm text-white disabled:opacity-50"
              disabled={!agent || assign.isPending}
              onClick={() => assign.mutate({ agent })}
            >
              Assign
            </button>
          </div>
        </div>

        <div>
          <div className="mb-1 text-xs font-medium text-neutral-600">Promise to pay</div>
          <div className="flex gap-2">
            <input
              type="date"
              className="rounded border border-neutral-300 px-2 py-1 text-sm"
              value={promiseDate}
              onChange={(e) => setPromiseDate(e.target.value)}
            />
            <input
              className="w-28 rounded border border-neutral-300 px-2 py-1 text-sm"
              value={promiseAmount}
              onChange={(e) => setPromiseAmount(e.target.value)}
              placeholder="minor units"
            />
            <button
              className="rounded bg-neutral-900 px-3 py-1 text-sm text-white disabled:opacity-50"
              disabled={!promiseDate || promise.isPending}
              onClick={() =>
                promise.mutate({
                  date: promiseDate,
                  amountMinor: Number(promiseAmount || 0),
                  currency: c.currency ?? "INR",
                })
              }
            >
              Record
            </button>
          </div>
        </div>

        <div className="flex gap-2">
          <button
            className="rounded border border-red-300 px-3 py-1 text-sm text-red-700 hover:bg-red-50 disabled:opacity-50"
            disabled={escalate.isPending}
            onClick={() => escalate.mutate({ reason: "manual escalation" })}
          >
            Escalate
          </button>
          <button
            className="rounded border border-green-300 px-3 py-1 text-sm text-green-700 hover:bg-green-50 disabled:opacity-50"
            disabled={resolve.isPending}
            onClick={() => resolve.mutate({ reason: "manual resolution" })}
          >
            Resolve
          </button>
        </div>
      </div>

      <div>
        <h2 className="mb-2 text-sm font-semibold text-neutral-700">Dunning timeline</h2>
        <table className="w-full overflow-hidden rounded border border-neutral-200 bg-white text-sm">
          <thead className="bg-neutral-100 text-left">
            <tr>
              <th className="px-4 py-2 font-medium">Fired</th>
              <th className="px-4 py-2 font-medium">Step</th>
              <th className="px-4 py-2 font-medium">DPD</th>
              <th className="px-4 py-2 font-medium">Action</th>
              <th className="px-4 py-2 font-medium">Channel</th>
            </tr>
          </thead>
          <tbody>
            {data.timeline.map((a) => (
              <tr key={a.id} className="border-t border-neutral-100">
                <td className="px-4 py-2 text-neutral-500">{a.firedAt}</td>
                <td className="px-4 py-2 font-mono text-xs">{a.stepId}</td>
                <td className="px-4 py-2">{a.dpdThreshold}</td>
                <td className="px-4 py-2">{a.actionType}</td>
                <td className="px-4 py-2 text-neutral-500">{a.channel ?? "—"}</td>
              </tr>
            ))}
            {data.timeline.length === 0 && (
              <tr>
                <td className="px-4 py-3 text-neutral-500" colSpan={5}>
                  No dunning actions yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded border border-neutral-200 bg-white p-3">
      <div className="text-xs text-neutral-500">{label}</div>
      <div className="font-medium">{value}</div>
    </div>
  );
}
