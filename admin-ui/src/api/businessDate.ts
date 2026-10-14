import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiSend } from "./client";

export interface BusinessDate {
  businessDate: string;
  overridden: boolean;
}

export function useBusinessDate() {
  return useQuery({
    queryKey: ["business-date"],
    queryFn: () => apiGet<BusinessDate>("/api/admin/business-date"),
    retry: false,
  });
}

export function useAdvanceBusinessDate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (days: number) =>
      apiSend<BusinessDate>("POST", `/api/admin/business-date/advance?days=${days}`),
    onSuccess: () => {
      void qc.invalidateQueries();
    },
  });
}

export function useSetBusinessDate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (date: string) => apiSend<BusinessDate>("PUT", "/api/admin/business-date", { date }),
    onSuccess: () => {
      void qc.invalidateQueries();
    },
  });
}
