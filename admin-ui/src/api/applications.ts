import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiSend } from "./client";

export interface OfferOption {
  tenorMonths: number;
  emiMinor: number;
  feeMinor: number;
  gstMinor: number;
  netDisbursalMinor: number;
  totalPayableMinor: number;
  effectiveAnnualRatePercent: string;
}

export interface AcceptedTerms {
  tenorMonths: number;
  principalMinor: number;
  emiMinor: number;
  netDisbursalMinor: number;
  effectiveAnnualRatePercent: string;
  esignReference: string;
}

export interface Application {
  id: string;
  customerId: string;
  status: string;
  requestedAmountMinor: number;
  currency: string;
  createdAt: string;
  updatedAt: string;
  offerOptions: OfferOption[];
  offerExpiresAt: string | null;
  acceptedTerms: AcceptedTerms | null;
}

export interface CreateApplication {
  customerId: string;
  productId: string;
  requestedAmountMinor: number;
  phone: string;
  dateOfBirth: string;
  declaredMonthlyIncomeMinor: number;
  employmentType: string;
  dataProcessingConsent: boolean;
  creditPullConsent: boolean;
  loanAgreementConsent: boolean;
}

export function useApplications(status?: string) {
  const q = status ? `?status=${encodeURIComponent(status)}` : "";
  return useQuery({
    queryKey: ["applications", status ?? "all"],
    queryFn: () => apiGet<Application[]>(`/api/applications${q}`),
    refetchInterval: 10_000,
  });
}

export function useApplication(id: string | undefined) {
  return useQuery({
    queryKey: ["application", id],
    queryFn: () => apiGet<Application>(`/api/applications/${id}`),
    enabled: !!id,
    refetchInterval: 5_000,
  });
}

export function useApplicationAction(id: string) {
  const qc = useQueryClient();
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ["application", id] });
    void qc.invalidateQueries({ queryKey: ["applications"] });
  };
  return {
    submit: useMutation({ mutationFn: () => apiSend("POST", `/api/applications/${id}/submit`), onSuccess: invalidate }),
    withdraw: useMutation({ mutationFn: () => apiSend("POST", `/api/applications/${id}/withdraw`), onSuccess: invalidate }),
    requestEsign: useMutation({ mutationFn: () => apiSend("POST", `/api/applications/${id}/esign`) }),
    accept: useMutation({
      mutationFn: (v: { tenorMonths: number; otpCode: string }) =>
        apiSend("POST", `/api/applications/${id}/accept`, v),
      onSuccess: invalidate,
    }),
  };
}

export function useCreateApplication() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateApplication) => apiSend<Application>("POST", "/api/applications", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["applications"] }),
  });
}
