import { useQuery } from "@tanstack/react-query";
import { apiGet } from "./client";
import type { Money } from "./loans";

export interface PortfolioMetrics {
  currency: string;
  totalLoans: number;
  activeLoans: number;
  outstandingPrincipal: Money;
  outstandingInterest: Money;
  outstandingPenal: Money;
  totalDisbursed: Money;
  dpdDistribution: Record<string, number>;
  npaRatioBps: number;
}

export interface ApplicationFunnel {
  submitted: number;
  approved: number;
  offered: number;
  accepted: number;
  disbursed: number;
  approvedRateBps: number;
  offeredRateBps: number;
  acceptedRateBps: number;
  disbursedRateBps: number;
}

export interface CollectionEfficiency {
  billed: Money;
  collected: Money;
  efficiencyBps: number;
}

export interface VintagePoint {
  disbursalMonth: string;
  loans: number;
  delinquent: number;
  delinquencyBps: number;
}

export interface VintageCurve {
  points: VintagePoint[];
}

export interface DailySnapshot {
  businessDate: string;
  currency: string;
  totalLoans: number;
  activeLoans: number;
  outstandingPrincipalMinor: number;
  disbursedMinor: number;
  npaRatioBps: number;
  dpdCurrent: number;
  dpd1To30: number;
  dpd31To60: number;
  dpd61To90: number;
  dpdNpa: number;
}

export interface ReconStatus {
  reconRunId: string;
  reconType: string;
  currency: string;
  balanced: boolean;
  diffMinor: number;
  matched: number;
  missingInLedger: number;
  missingInBank: number;
  amountMismatch: number;
  duplicate: number;
  runAt: string;
}

export interface AuditLogRecord {
  eventId: string;
  actorId: string;
  actorRoles: string;
  action: string;
  targetType: string;
  targetId: string;
  beforeSummary: string | null;
  afterSummary: string | null;
  outcome: string;
  correlationId: string | null;
  traceId: string | null;
  recordedAt: string;
}

/** Basis points (per-ten-thousand) to a percentage string, e.g. 2500 -> "25.00%". */
export function formatBps(bps: number): string {
  return `${(bps / 100).toFixed(2)}%`;
}

const DASHBOARD_REFETCH_MS = 15_000;

export function usePortfolio() {
  return useQuery({
    queryKey: ["reporting", "portfolio"],
    queryFn: () => apiGet<PortfolioMetrics>(`/api/reporting/portfolio`),
    refetchInterval: DASHBOARD_REFETCH_MS,
  });
}

export function useFunnel() {
  return useQuery({
    queryKey: ["reporting", "funnel"],
    queryFn: () => apiGet<ApplicationFunnel>(`/api/reporting/funnel`),
    refetchInterval: DASHBOARD_REFETCH_MS,
  });
}

export function useCollectionEfficiency() {
  return useQuery({
    queryKey: ["reporting", "collectionEfficiency"],
    queryFn: () => apiGet<CollectionEfficiency>(`/api/reporting/collection-efficiency`),
    refetchInterval: DASHBOARD_REFETCH_MS,
  });
}

export function useVintage() {
  return useQuery({
    queryKey: ["reporting", "vintage"],
    queryFn: () => apiGet<VintageCurve>(`/api/reporting/vintage`),
    refetchInterval: DASHBOARD_REFETCH_MS,
  });
}

export function useSnapshots(limit = 30) {
  return useQuery({
    queryKey: ["reporting", "snapshots", limit],
    queryFn: () => apiGet<DailySnapshot[]>(`/api/reporting/snapshots?limit=${limit}`),
  });
}

export function useTrialBalanceStatus() {
  return useQuery({
    queryKey: ["reporting", "recon", "TRIAL_BALANCE"],
    queryFn: () => apiGet<ReconStatus>(`/api/reporting/recon/TRIAL_BALANCE`),
    refetchInterval: DASHBOARD_REFETCH_MS,
    retry: false,
  });
}

export interface AuditFilters {
  actor?: string;
  action?: string;
  targetType?: string;
  targetId?: string;
  limit?: number;
}

export function useAuditLog(filters: AuditFilters) {
  const params = new URLSearchParams();
  if (filters.actor) params.set("actor", filters.actor);
  if (filters.action) params.set("action", filters.action);
  if (filters.targetType) params.set("targetType", filters.targetType);
  if (filters.targetId) params.set("targetId", filters.targetId);
  params.set("limit", String(filters.limit ?? 100));
  const qs = params.toString();
  return useQuery({
    queryKey: ["audit", qs],
    queryFn: () => apiGet<AuditLogRecord[]>(`/api/audit?${qs}`),
  });
}
