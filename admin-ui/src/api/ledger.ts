import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiSend } from "./client";
import type { Money } from "./loans";

export interface Posting {
  account: string;
  side: "DEBIT" | "CREDIT";
  amount: Money;
  loanId: string | null;
  customerId: string | null;
}

export interface JournalEntry {
  id: string;
  sourceEventId: string;
  sourceEventType: string;
  currency: string;
  postings: Posting[];
  loanId: string | null;
  customerId: string | null;
  reversesEntryId: string | null;
  occurredAt: string;
}

export interface TrialBalance {
  totalDebitMinorUnits: number;
  totalCreditMinorUnits: number;
  diffMinorUnits: number;
  balanced: boolean;
  normalBalances: Record<string, number>;
}

export function useEntries(filters: { loanId?: string; date?: string; account?: string } = {}) {
  const params = new URLSearchParams();
  if (filters.loanId) params.set("loanId", filters.loanId);
  if (filters.date) params.set("date", filters.date);
  if (filters.account) params.set("account", filters.account);
  const query = params.toString();
  return useQuery({
    queryKey: ["ledger-entries", filters],
    queryFn: () => apiGet<JournalEntry[]>(`/api/journal-entries${query ? `?${query}` : ""}`),
  });
}

export function useSubLedger(loanId: string | undefined) {
  return useQuery({
    queryKey: ["sub-ledger", loanId],
    queryFn: () => apiGet<JournalEntry[]>(`/api/ledger/loans/${loanId}`),
    enabled: !!loanId,
  });
}

export function useTrialBalance() {
  return useQuery({
    queryKey: ["trial-balance"],
    queryFn: () => apiGet<TrialBalance>("/api/ledger/trial-balance"),
    refetchInterval: 15_000,
  });
}

export function useReverseEntry() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { id: string; reason: string }) =>
      apiSend("POST", `/api/journal-entries/${v.id}/reverse`, { reason: v.reason }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["ledger-entries"] });
      void qc.invalidateQueries({ queryKey: ["trial-balance"] });
    },
  });
}
