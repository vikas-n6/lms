import { useState } from "react";
import { DLT_TOPICS, useDltMessages, useReplayDlt } from "../api/dlt";

export function DltViewerPage() {
  const [topic, setTopic] = useState(DLT_TOPICS[3]); // loan.events.dlt by default
  const messages = useDltMessages(topic);
  const replay = useReplayDlt(topic);

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold">Dead-letter viewer</h1>

      <div className="flex items-center gap-3">
        <label className="text-sm text-neutral-600">Topic</label>
        <select
          className="rounded border border-neutral-300 px-2 py-1 text-sm"
          value={topic}
          onChange={(e) => setTopic(e.target.value)}
        >
          {DLT_TOPICS.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
        <button
          className="rounded border border-neutral-300 px-3 py-1 text-sm hover:bg-neutral-100"
          onClick={() => void messages.refetch()}
        >
          Refresh
        </button>
      </div>

      {messages.isError && <p className="text-sm text-red-600">Failed to load (broker reachable?).</p>}

      <div className="overflow-x-auto rounded-lg border border-neutral-200 bg-white">
        <table className="w-full text-sm">
          <thead className="bg-neutral-50 text-left text-xs uppercase text-neutral-500">
            <tr>
              <th className="px-4 py-2">Part/Offset</th>
              <th className="px-4 py-2">Key</th>
              <th className="px-4 py-2">Original topic</th>
              <th className="px-4 py-2">Exception</th>
              <th className="px-4 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {(messages.data ?? []).map((m) => (
              <tr key={`${m.partition}-${m.offset}`} className="border-t border-neutral-100 align-top">
                <td className="px-4 py-2 tabular-nums">
                  {m.partition}/{m.offset}
                </td>
                <td className="px-4 py-2 text-neutral-600">{m.key ?? "—"}</td>
                <td className="px-4 py-2 text-neutral-600">{m.originalTopic ?? "—"}</td>
                <td className="px-4 py-2 text-neutral-600">
                  <div className="font-medium">{m.exceptionClass ?? "—"}</div>
                  <div className="text-xs text-neutral-500">{m.exceptionMessage}</div>
                </td>
                <td className="px-4 py-2">
                  <button
                    className="rounded border border-neutral-300 px-3 py-1 text-xs hover:bg-neutral-100 disabled:opacity-50"
                    disabled={replay.isPending}
                    onClick={() => replay.mutate({ partition: m.partition, offset: m.offset })}
                  >
                    Replay
                  </button>
                </td>
              </tr>
            ))}
            {(messages.data ?? []).length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-neutral-400" colSpan={5}>
                  No dead-letter records on this topic.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {replay.isSuccess && replay.data && (
        <p className="text-sm text-emerald-600">
          Replayed to {replay.data.replayedTo}.
        </p>
      )}
      {replay.isError && <p className="text-sm text-red-600">Replay failed.</p>}
    </div>
  );
}
