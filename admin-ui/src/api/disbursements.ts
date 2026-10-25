import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiSend } from "./client";

export interface DisbursementSummary {
  id: string;
  loanId: string;
  loanNumber: string;
  status: string;
  netMinorUnits: number;
  currency: string;
  railRef: string | null;
  utr: string | null;
  attempts: number;
  failureReason: string | null;
}

export interface PayoutAttempt {
  id: string;
  disbursementId: string;
  attemptNumber: number;
  idempotencyKey: string;
  status: string;
  failureCode: string | null;
  startedAt: string;
  finishedAt: string | null;
}

export interface DisbursementDetail {
  disbursement: DisbursementSummary;
  attempts: PayoutAttempt[];
  temporalUiLink: string;
}

export function useDisbursements() {
  return useQuery({
    queryKey: ["disbursements"],
    queryFn: () => apiGet<DisbursementSummary[]>("/api/disbursements"),
  });
}

export function useDisbursement(id: string | undefined) {
  return useQuery({
    queryKey: ["disbursement", id],
    queryFn: () => apiGet<DisbursementDetail>(`/api/disbursements/${id}`),
    enabled: !!id,
  });
}

export function useDisbursementAction(id: string) {
  const qc = useQueryClient();
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ["disbursements"] });
    void qc.invalidateQueries({ queryKey: ["disbursement", id] });
  };
  return {
    retry: useMutation({
      mutationFn: () => apiSend("POST", `/api/disbursements/${id}/retry`),
      onSuccess: invalidate,
    }),
    cancel: useMutation({
      mutationFn: () => apiSend("POST", `/api/disbursements/${id}/cancel`),
      onSuccess: invalidate,
    }),
  };
}
