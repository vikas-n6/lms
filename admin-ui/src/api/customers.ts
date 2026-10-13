import { useQuery } from "@tanstack/react-query";
import { apiGet } from "./client";

export interface Customer {
  id: string;
  displayName: string;
  status: string;
  createdAt: string;
}

export function useCustomer(id: string | undefined) {
  return useQuery({
    queryKey: ["customer", id],
    queryFn: () => apiGet<Customer>(`/api/customers/${id}`),
    enabled: !!id,
  });
}

export function useConsents(id: string | undefined) {
  return useQuery({
    queryKey: ["consents", id],
    queryFn: () => apiGet<string[]>(`/api/customers/${id}/consents`),
    enabled: !!id,
  });
}
