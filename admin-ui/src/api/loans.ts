import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiSend } from "./client";

export interface Money {
  minorUnits: number;
  currency: string;
}

export interface LoanSummary {
  id: string;
  loanNumber: string;
  customerId: string;
  state: string;
  principal: Money;
  tenorMonths: number;
  disbursementDate: string | null;
}

export interface LoanBalances {
  outstandingPrincipal: Money;
  interestOutstanding: Money;
  penalOutstanding: Money;
  totalDueNow: Money;
  nextDueDate: string | null;
}

export interface ScheduleLine {
  instalmentNumber: number;
  dueDate: string;
  principal: Money;
  interest: Money;
  paidPrincipal: Money;
  paidInterest: Money;
  penalAccrued: Money;
  penalPaid: Money;
  provisional: boolean;
  status: string;
}

export interface StateHistoryEntry {
  fromState: string | null;
  toState: string;
  cause: string;
  legal: boolean;
  recordedAt: string;
}

export interface LoanDetail {
  loan: LoanSummary;
  balances: LoanBalances;
  schedule: ScheduleLine[];
  history: StateHistoryEntry[];
}

export interface ForeclosureQuote {
  loanId: string;
  asOf: string;
  amount: Money;
}

export function useLoans(filters: { state?: string; customerId?: string } = {}) {
  const params = new URLSearchParams();
  if (filters.state) params.set("state", filters.state);
  if (filters.customerId) params.set("customerId", filters.customerId);
  const query = params.toString();
  return useQuery({
    queryKey: ["loans", filters],
    queryFn: () => apiGet<LoanSummary[]>(`/api/loans${query ? `?${query}` : ""}`),
  });
}

export function useLoan(id: string | undefined) {
  return useQuery({
    queryKey: ["loan", id],
    queryFn: () => apiGet<LoanDetail>(`/api/loans/${id}`),
    enabled: !!id,
  });
}

export function useForeclosureQuote(id: string | undefined, date?: string) {
  return useQuery({
    queryKey: ["foreclosure", id, date],
    queryFn: () =>
      apiGet<ForeclosureQuote>(
        `/api/loans/${id}/foreclosure-quote${date ? `?date=${date}` : ""}`,
      ),
    enabled: !!id,
  });
}

export function useSimulateDisbursement(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (date?: string) =>
      apiSend("POST", `/api/admin/loans/${id}/simulate-disbursement${date ? `?date=${date}` : ""}`),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["loan", id] });
      void qc.invalidateQueries({ queryKey: ["loans"] });
    },
  });
}

export function useInjectRepayment(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { amountMinor: number; currency?: string; repaymentId?: string }) =>
      apiSend("POST", `/api/admin/loans/${id}/inject-repayment`, v),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["loan", id] });
    },
  });
}

export function useRunAccrual() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (date?: string) =>
      apiSend("POST", `/api/admin/jobs/interest-accrual/run${date ? `?date=${date}` : ""}`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["loans"] }),
  });
}
