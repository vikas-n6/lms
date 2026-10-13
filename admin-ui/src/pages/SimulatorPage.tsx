import { useSimActions, useSimConfigs, useSmsOutbox, type SimConfig } from "../api/simulators";

const PRESETS = ["healthy", "slow", "flaky", "down"];

export function SimulatorPage() {
  const { data: configs } = useSimConfigs();
  const { data: sms } = useSmsOutbox();
  const actions = useSimActions();

  function setFailureRate(simulator: string, config: SimConfig, rate: number) {
    actions.putConfig.mutate({ simulator, config: { ...config, failureRate: rate } });
  }

  return (
    <section className="max-w-4xl">
      <h1 className="mb-4 text-xl font-semibold">Simulator control</h1>

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <span className="text-sm text-neutral-600">Apply preset to all:</span>
        {PRESETS.map((p) => (
          <button key={p} className="rounded border border-neutral-300 px-3 py-1 text-sm hover:bg-neutral-100"
            onClick={() => actions.applyPresetAll.mutate(p)}>{p}</button>
        ))}
        <button className="ml-auto rounded bg-neutral-900 px-3 py-1 text-sm text-white" onClick={() => actions.reset.mutate()}>Reset</button>
      </div>

      <table className="mb-8 w-full overflow-hidden rounded border border-neutral-200 bg-white text-sm">
        <thead className="bg-neutral-100 text-left">
          <tr><th className="px-3 py-2">Simulator</th><th className="px-3 py-2">Latency</th><th className="px-3 py-2">Failure</th><th className="px-3 py-2">Timeout</th><th className="px-3 py-2">Dup cb</th><th className="px-3 py-2">Quick failure</th></tr>
        </thead>
        <tbody>
          {configs && Object.entries(configs).map(([name, c]) => (
            <tr key={name} className="border-t border-neutral-100">
              <td className="px-3 py-2 font-medium">{name}</td>
              <td className="px-3 py-2">{c.latencyMsMin}–{c.latencyMsMax}ms</td>
              <td className="px-3 py-2">{(c.failureRate * 100).toFixed(0)}%</td>
              <td className="px-3 py-2">{(c.timeoutRate * 100).toFixed(0)}%</td>
              <td className="px-3 py-2">{(c.duplicateCallbackRate * 100).toFixed(0)}%</td>
              <td className="px-3 py-2">
                <input type="range" min={0} max={1} step={0.1} defaultValue={c.failureRate}
                  onMouseUp={(e) => setFailureRate(name, c, Number((e.target as HTMLInputElement).value))} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <h2 className="mb-2 text-sm font-semibold text-neutral-700">SMS outbox (live)</h2>
      <table className="w-full overflow-hidden rounded border border-neutral-200 bg-white text-sm">
        <thead className="bg-neutral-100 text-left"><tr><th className="px-3 py-2">To</th><th className="px-3 py-2">Body</th><th className="px-3 py-2">Sent</th></tr></thead>
        <tbody>
          {sms && [...sms].reverse().map((m) => (
            <tr key={m.id} className="border-t border-neutral-100">
              <td className="px-3 py-2 font-mono text-xs">{m.to}</td>
              <td className="px-3 py-2">{m.body}</td>
              <td className="px-3 py-2 text-neutral-500">{new Date(m.sentAt).toLocaleTimeString()}</td>
            </tr>
          ))}
          {sms && sms.length === 0 && <tr><td className="px-3 py-3 text-neutral-500" colSpan={3}>No messages.</td></tr>}
        </tbody>
      </table>
    </section>
  );
}
