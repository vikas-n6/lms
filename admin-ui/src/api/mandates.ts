import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiSend } from "./client";

export interface MandateView {
  id: string;
  loanId: string;
  customerId: string;
  status: string;
}

export function useMandate(loanId: string | undefined) {
  return useQuery({
    queryKey: ["mandate", loanId],
    queryFn: () => apiGet<MandateView>(`/api/mandates/${loanId}`),
    enabled: !!loanId,
    retry: false,
  });
}

export function useRegisterMandate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { loanId: string; customerId: string }) =>
      apiSend<MandateView>("POST", "/api/mandates", v),
    onSuccess: (_data, variables) =>
      void qc.invalidateQueries({ queryKey: ["mandate", variables.loanId] }),
  });
}
