import { useState } from "react";
import { useReview, useReviewQueue, type Explain } from "../api/decisions";

function ReviewRow({ d }: { d: Explain }) {
  const review = useReview(d.decisionId);
  const [limit, setLimit] = useState("1500000");
  const [note, setNote] = useState("");

  return (
    <div className="mb-3 rounded border border-neutral-200 bg-white p-3 text-sm">
      <div className="mb-1 flex items-center gap-2">
        <span className="font-mono text-xs">{d.applicationId}</span>
        <span className="rounded bg-amber-100 px-2 py-0.5 text-xs text-amber-800">REFER</span>
        {d.reasonCodes.map((c) => <code key={c} className="rounded bg-neutral-100 px-1 text-xs">{c}</code>)}
      </div>
      <div className="mb-2 text-neutral-600">
        bureau {d.bureau.available ? `score ${d.bureau.score}` : "unavailable"}
      </div>
      <div className="flex flex-wrap items-end gap-2">
        <label>Offered limit (minor)
          <input className="ml-1 w-32 rounded border border-neutral-300 px-2 py-1" value={limit} onChange={(e) => setLimit(e.target.value)} />
        </label>
        <label>Note
          <input className="ml-1 w-56 rounded border border-neutral-300 px-2 py-1" value={note} onChange={(e) => setNote(e.target.value)} />
        </label>
        <button className="rounded bg-neutral-900 px-3 py-1.5 text-white"
          onClick={() => review.mutate({ outcome: "APPROVE", note, offeredLimitMinor: Number(limit) })}>
          Approve
        </button>
        <button className="rounded border border-red-300 px-3 py-1.5 text-red-700"
          onClick={() => review.mutate({ outcome: "REJECT", note, offeredLimitMinor: null })}>
          Reject
        </button>
      </div>
    </div>
  );
}

export function ReviewQueuePage() {
  const { data, isLoading } = useReviewQueue();
  return (
    <section className="max-w-3xl">
      <h1 className="mb-4 text-xl font-semibold">Manual review queue</h1>
      {isLoading && <p className="text-sm text-neutral-500">Loading…</p>}
      {data && data.length === 0 && <p className="text-sm text-neutral-500">No decisions awaiting review.</p>}
      {data && data.map((d) => <ReviewRow key={d.decisionId} d={d} />)}
    </section>
  );
}
