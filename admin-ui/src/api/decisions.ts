import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiSend } from "./client";

export interface RuleLine {
  rule: string;
  status: string;
  reasonCode: string | null;
  detail: string;
}

export interface Explain {
  decisionId: string;
  applicationId: string;
  outcome: string;
  reasonCodes: string[];
  rules: RuleLine[];
  ruleSetVersion: string;
  offeredLimitMinor: number | null;
  currency: string;
  bureau: { available: boolean; score: number; overdue: boolean; activeAccounts: number };
  manualOverride: boolean;
  reviewNote: string | null;
}

export function useExplain(id: string | undefined) {
  return useQuery({
    queryKey: ["decision", id],
    queryFn: () => apiGet<Explain>(`/api/decisions/${id}/explain`),
    enabled: !!id,
  });
}

export function useReviewQueue() {
  return useQuery({
    queryKey: ["review-queue"],
    queryFn: () => apiGet<Explain[]>("/api/decisions/review-queue"),
    refetchInterval: 10_000,
  });
}

export function useReview(decisionId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { outcome: string; note: string; offeredLimitMinor: number | null }) =>
      apiSend("POST", `/api/decisions/${decisionId}/review`, v),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["review-queue"] });
      void qc.invalidateQueries({ queryKey: ["applications"] });
    },
  });
}
