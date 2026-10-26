import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiSend } from "./client";
import type { Money } from "./loans";

export interface ReconRun {
  id: string;
  reconDate: string;
  currency: string;
  matched: number;
  missingInLedger: number;
  missingInBank: number;
  amountMismatch: number;
  duplicate: number;
  runAt: string;
}

export interface ReconItem {
  reference: string;
  classification: string;
  externalAmount: Money | null;
  ledgerAmount: Money | null;
}

export interface StoredReconItem {
  id: string;
  runId: string;
  item: ReconItem;
  resolved: boolean;
  resolutionNote: string | null;
}

export function useReconRuns() {
  return useQuery({
    queryKey: ["reconRuns"],
    queryFn: () => apiGet<ReconRun[]>(`/api/recon/runs`),
  });
}

export function useReconItems(runId: string | undefined) {
  return useQuery({
    queryKey: ["reconItems", runId],
    queryFn: () => apiGet<StoredReconItem[]>(`/api/recon/runs/${runId}/items`),
    enabled: !!runId,
  });
}

export function useResolveReconItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { itemId: string; note: string }) =>
      apiSend("POST", `/api/recon/items/${v.itemId}/resolve`, { note: v.note }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["reconItems"] }),
  });
}
