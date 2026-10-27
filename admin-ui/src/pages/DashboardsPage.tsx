import { formatMoney } from "../lib/money";
import { useDashboardStream } from "../api/dashboardStream";
import {
  formatBps,
  useCollectionEfficiency,
  useFunnel,
  usePortfolio,
  useTrialBalanceStatus,
  useVintage,
} from "../api/reporting";

const DPD_ORDER = ["CURRENT", "DPD_1_30", "DPD_31_60", "DPD_61_90", "NPA"];
const DPD_COLORS: Record<string, string> = {
  CURRENT: "bg-emerald-500",
  DPD_1_30: "bg-yellow-500",
  DPD_31_60: "bg-orange-500",
  DPD_61_90: "bg-red-500",
  NPA: "bg-red-800",
};

function Tile({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="rounded-lg border border-neutral-200 bg-white p-4">
      <div className="text-xs uppercase tracking-wide text-neutral-500">{label}</div>
      <div className="mt-1 text-2xl font-semibold tabular-nums">{value}</div>
      {sub && <div className="mt-1 text-xs text-neutral-500">{sub}</div>}
    </div>
  );
}

function Bar({ label, value, max, color }: { label: string; value: number; max: number; color: string }) {
  const pct = max > 0 ? Math.round((value / max) * 100) : 0;
  return (
    <div className="flex items-center gap-3 text-sm">
      <div className="w-28 shrink-0 text-neutral-600">{label}</div>
      <div className="h-5 flex-1 rounded bg-neutral-100">
        <div className={`h-5 rounded ${color}`} style={{ width: `${pct}%` }} />
      </div>
      <div className="w-12 shrink-0 text-right tabular-nums">{value}</div>
    </div>
  );
}

export function DashboardsPage() {
  useDashboardStream();
  const portfolio = usePortfolio();
  const funnel = useFunnel();
  const collection = useCollectionEfficiency();
  const vintage = useVintage();
  const trialBalance = useTrialBalanceStatus();

  const p = portfolio.data;
  const f = funnel.data;

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Dashboards</h1>
        <span className="text-xs text-neutral-500">live · updates stream + poll</span>
      </div>

      {portfolio.isError && <p className="text-sm text-red-600">Failed to load portfolio.</p>}

      {p && (
        <section className="space-y-3">
          <h2 className="text-sm font-medium text-neutral-500">Portfolio</h2>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <Tile label="Active loans" value={String(p.activeLoans)} sub={`${p.totalLoans} total`} />
            <Tile
              label="Outstanding principal"
              value={formatMoney(p.outstandingPrincipal.minorUnits, p.outstandingPrincipal.currency)}
            />
            <Tile
              label="Disbursed"
              value={formatMoney(p.totalDisbursed.minorUnits, p.totalDisbursed.currency)}
            />
            <Tile label="NPA ratio" value={formatBps(p.npaRatioBps)} />
          </div>

          <div className="rounded-lg border border-neutral-200 bg-white p-4">
            <div className="mb-3 text-sm font-medium">DPD distribution</div>
            <div className="space-y-2">
              {DPD_ORDER.map((bucket) => (
                <Bar
                  key={bucket}
                  label={bucket}
                  value={p.dpdDistribution[bucket] ?? 0}
                  max={p.totalLoans}
                  color={DPD_COLORS[bucket]}
                />
              ))}
            </div>
          </div>
        </section>
      )}

      {f && (
        <section className="space-y-3">
          <h2 className="text-sm font-medium text-neutral-500">Application funnel</h2>
          <div className="rounded-lg border border-neutral-200 bg-white p-4 space-y-2">
            <Bar label="Submitted" value={f.submitted} max={f.submitted} color="bg-sky-500" />
            <Bar label="Approved" value={f.approved} max={f.submitted} color="bg-sky-500" />
            <Bar label="Offered" value={f.offered} max={f.submitted} color="bg-sky-500" />
            <Bar label="Accepted" value={f.accepted} max={f.submitted} color="bg-sky-500" />
            <Bar label="Disbursed" value={f.disbursed} max={f.submitted} color="bg-sky-600" />
            <div className="pt-2 text-xs text-neutral-500">
              Approve {formatBps(f.approvedRateBps)} · Offer {formatBps(f.offeredRateBps)} · Accept{" "}
              {formatBps(f.acceptedRateBps)} · Disburse {formatBps(f.disbursedRateBps)}
            </div>
          </div>
        </section>
      )}

      <section className="grid gap-3 md:grid-cols-2">
        <div className="rounded-lg border border-neutral-200 bg-white p-4">
          <div className="mb-2 text-sm font-medium">Collection efficiency</div>
          {collection.data ? (
            <>
              <div className="text-2xl font-semibold">{formatBps(collection.data.efficiencyBps)}</div>
              <div className="mt-1 text-xs text-neutral-500">
                Collected {formatMoney(collection.data.collected.minorUnits, collection.data.collected.currency)} of{" "}
                {formatMoney(collection.data.billed.minorUnits, collection.data.billed.currency)} billed
              </div>
            </>
          ) : (
            <p className="text-sm text-neutral-400">—</p>
          )}
        </div>
        <div className="rounded-lg border border-neutral-200 bg-white p-4">
          <div className="mb-2 text-sm font-medium">Ledger trial balance</div>
          {trialBalance.data ? (
            <div className={trialBalance.data.balanced ? "text-emerald-600" : "text-red-600"}>
              <div className="text-2xl font-semibold">
                {trialBalance.data.balanced ? "Balanced" : "IMBALANCE"}
              </div>
              <div className="mt-1 text-xs text-neutral-500">
                diff {trialBalance.data.diffMinor} minor · last run{" "}
                {new Date(trialBalance.data.runAt).toLocaleString()}
              </div>
            </div>
          ) : (
            <p className="text-sm text-neutral-400">No run recorded yet.</p>
          )}
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-sm font-medium text-neutral-500">Vintage (30+ DPD by disbursal month)</h2>
        <div className="overflow-x-auto rounded-lg border border-neutral-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-neutral-50 text-left text-xs uppercase text-neutral-500">
              <tr>
                <th className="px-4 py-2">Month</th>
                <th className="px-4 py-2 text-right">Loans</th>
                <th className="px-4 py-2 text-right">30+ DPD</th>
                <th className="px-4 py-2 text-right">Delinquency</th>
              </tr>
            </thead>
            <tbody>
              {(vintage.data?.points ?? []).map((pt) => (
                <tr key={pt.disbursalMonth} className="border-t border-neutral-100">
                  <td className="px-4 py-2">{pt.disbursalMonth}</td>
                  <td className="px-4 py-2 text-right tabular-nums">{pt.loans}</td>
                  <td className="px-4 py-2 text-right tabular-nums">{pt.delinquent}</td>
                  <td className="px-4 py-2 text-right tabular-nums">{formatBps(pt.delinquencyBps)}</td>
                </tr>
              ))}
              {(vintage.data?.points ?? []).length === 0 && (
                <tr>
                  <td className="px-4 py-6 text-center text-neutral-400" colSpan={4}>
                    No disbursed cohorts yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
