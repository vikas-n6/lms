import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiSend } from "./client";

export interface DltRecordView {
  partition: number;
  offset: number;
  timestamp: number;
  key: string | null;
  originalTopic: string | null;
  originalPartition: string | null;
  exceptionClass: string | null;
  exceptionMessage: string | null;
}

export interface ReplayResult {
  dltTopic: string;
  offset: number;
  replayedTo: string;
}

/** The dead-letter topics in the catalogue (docs/architecture.md), one per domain topic. */
export const DLT_TOPICS = [
  "customer.events.dlt",
  "application.events.dlt",
  "decision.events.dlt",
  "loan.events.dlt",
  "disbursement.events.dlt",
  "repayment.events.dlt",
  "collections.events.dlt",
  "ledger.events.dlt",
  "notification.events.dlt",
  "audit.events.dlt",
];

export function useDltMessages(topic: string, limit = 50) {
  return useQuery({
    queryKey: ["dlt", topic, limit],
    queryFn: () => apiGet<DltRecordView[]>(`/api/admin/dlt/${topic}?limit=${limit}`),
  });
}

export function useReplayDlt(topic: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { partition: number; offset: number }) =>
      apiSend<ReplayResult>("POST", `/api/admin/dlt/${topic}/replay`, v),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["dlt", topic] }),
  });
}
