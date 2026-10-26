import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiSend } from "./client";

export interface CollectionCase {
  id: string;
  loanId: string;
  loanNumber: string;
  status: string;
  bucket: string;
  daysPastDue: number;
  assignedAgent: string | null;
  promiseDate: string | null;
  promiseAmountMinor: number | null;
  currency: string | null;
  planVersion: number;
}

export interface DunningAction {
  id: string;
  caseId: string;
  stepId: string;
  dpdThreshold: number;
  actionType: string;
  channel: string | null;
  templateId: string | null;
  firedAt: string;
}

export interface CaseDetail {
  theCase: CollectionCase;
  timeline: DunningAction[];
}

export function useCases(filters: { bucket?: string; status?: string; agent?: string } = {}) {
  const params = new URLSearchParams();
  if (filters.bucket) params.set("bucket", filters.bucket);
  if (filters.status) params.set("status", filters.status);
  if (filters.agent) params.set("agent", filters.agent);
  const query = params.toString();
  return useQuery({
    queryKey: ["cases", filters],
    queryFn: () => apiGet<CollectionCase[]>(`/api/collections/cases${query ? `?${query}` : ""}`),
  });
}

export function useCase(id: string | undefined) {
  return useQuery({
    queryKey: ["case", id],
    queryFn: () => apiGet<CaseDetail>(`/api/collections/cases/${id}`),
    enabled: !!id,
  });
}

function useCaseAction(caseId: string, path: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body?: unknown) =>
      apiSend("POST", `/api/collections/cases/${caseId}/${path}`, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["case", caseId] });
      void qc.invalidateQueries({ queryKey: ["cases"] });
    },
  });
}

export const useAssignAgent = (caseId: string) => useCaseAction(caseId, "assign");
export const useRecordPromise = (caseId: string) => useCaseAction(caseId, "promise");
export const useEscalateCase = (caseId: string) => useCaseAction(caseId, "escalate");
export const useResolveCase = (caseId: string) => useCaseAction(caseId, "resolve");
