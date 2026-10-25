import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiSend } from "./client";

export interface PaymentLink {
  linkId: string;
  url: string;
}

export interface ManualReceiptResponse {
  repaymentId: string | null;
  status: string;
}

export function useCreatePaymentLink() {
  return useMutation({
    mutationFn: (v: {
      loanId: string;
      amountMinor: number;
      currency: string;
      purpose?: string;
      foreclosureQuoteId?: string;
    }) => apiSend<PaymentLink>("POST", "/api/repayments", v),
  });
}

export function useManualReceipt() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: {
      loanId: string;
      amountMinor: number;
      currency: string;
      bankReference: string;
      purpose?: string;
      foreclosureQuoteId?: string;
    }) => apiSend<ManualReceiptResponse>("POST", "/api/repayments/manual", v),
    onSuccess: (_data, variables) => {
      void qc.invalidateQueries({ queryKey: ["loan", variables.loanId] });
    },
  });
}
