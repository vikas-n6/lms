import { useState } from "react";
import { useEntries, useReverseEntry, useTrialBalance, type JournalEntry } from "../api/ledger";
import { formatMoney } from "../lib/money";

export function LedgerPage() {
  const [loanId, setLoanId] = useState("");
  const [account, setAccount] = useState("");
  const entries = useEntries({ loanId: loanId || undefined, account: account || undefined });
  const trialBalance = useTrialBalance();
  const reverse = useReverseEntry();

  return (
    <section className="space-y-6">
      <h1 className="text-xl font-semibold">Ledger explorer</h1>

      <div className="rounded border border-neutral-200 bg-white p-4">
        <div className="mb-2 flex items-center gap-2">
          <h2 className="text-lg font-semibold">Trial balance</h2>
          {trialBalance.data && (
            <span
              className={`rounded px-2 py-0.5 text-xs font-medium ${
                trialBalance.data.balanced
                  ? "bg-green-100 text-green-800"
                  : "bg-red-100 text-red-800"
              }`}
            >
              {trialBalance.data.balanced
                ? "balanced"
                : `off by ${trialBalance.data.diffMinorUnits}`}
            </span>
          )}
        </div>
        {trialBalance.data && (
          <table className="w-full text-sm">
            <tbody>
              {Object.entries(trialBalance.data.normalBalances).map(([account, minor]) => (
                <tr key={account} className="border-t border-neutral-100">
                  <td className="py-1 font-mono text-xs">{account}</td>
                  <td className="py-1 text-right">{minor}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="flex flex-wrap gap-3 text-sm">
        <label>
          Loan id{" "}
          <input
            value={loanId}
            onChange={(e) => setLoanId(e.target.value)}
            className="ml-1 w-72 rounded border border-neutral-300 px-2 py-1 font-mono text-xs"
          />
        </label>
        <label>
          Account{" "}
          <input
            value={account}
            onChange={(e) => setAccount(e.target.value)}
            placeholder="e.g. 1100"
            className="ml-1 w-28 rounded border border-neutral-300 px-2 py-1"
          />
        </label>
      </div>

      {entries.isLoading && <p className="text-sm text-neutral-500">Loading…</p>}
      {entries.isError && <p className="text-sm text-red-700">Could not load entries.</p>}
      {entries.data && (
        <div className="space-y-3">
          {entries.data.map((entry) => (
            <EntryCard
              key={entry.id}
              entry={entry}
              onReverse={(reason) => reverse.mutate({ id: entry.id, reason })}
            />
          ))}
          {entries.data.length === 0 && <p className="text-sm text-neutral-500">No entries.</p>}
        </div>
      )}
    </section>
  );
}

function EntryCard({
  entry,
  onReverse,
}: {
  entry: JournalEntry;
  onReverse: (reason: string) => void;
}) {
  const isReversal = entry.reversesEntryId !== null;
  return (
    <div className="rounded border border-neutral-200 bg-white p-4">
      <div className="mb-2 flex items-center justify-between">
        <div>
          <span className="font-medium">{entry.sourceEventType}</span>
          {isReversal && (
            <span className="ml-2 rounded bg-amber-100 px-2 py-0.5 text-xs text-amber-800">
              reversal
            </span>
          )}
          <div className="font-mono text-xs text-neutral-500">{entry.id}</div>
        </div>
        {!isReversal && (
          <button
            onClick={() => {
              const reason = window.prompt("Reversal reason?");
              if (reason) onReverse(reason);
            }}
            className="rounded border border-neutral-300 px-2 py-1 text-xs hover:bg-neutral-100"
          >
            Reverse
          </button>
        )}
      </div>
      <table className="w-full text-sm">
        <tbody>
          {entry.postings.map((p, i) => (
            <tr key={i} className="border-t border-neutral-100">
              <td className="py-1 font-mono text-xs">{p.account}</td>
              <td className="py-1">{p.side}</td>
              <td className="py-1 text-right">
                {formatMoney(p.amount.minorUnits, p.amount.currency)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
