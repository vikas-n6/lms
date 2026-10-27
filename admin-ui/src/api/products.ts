import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiSend } from "./client";

export interface ProductView {
  productId: string;
  version: number;
  currency: string;
  minPrincipalMinor: number;
  maxPrincipalMinor: number;
  principalStepMinor: number;
  tenors: number[];
  aprPercent: string;
  feePercent: string;
  gstPercent: string;
  penalAprPercent: string;
  graceDays: number;
  foreclosureChargePercent: string;
  effectiveFrom: string;
  createdAt: string;
}

export interface NewProductVersion {
  currency: string;
  minPrincipalMinor: number;
  maxPrincipalMinor: number;
  principalStepMinor: number;
  tenors: number[];
  aprPercent: string;
  feePercent: string;
  gstPercent: string;
  penalAprPercent: string;
  graceDays: number;
  foreclosureChargePercent: string;
  effectiveFrom: string;
}

export function useCurrentProduct() {
  return useQuery({
    queryKey: ["products", "current"],
    queryFn: () => apiGet<ProductView>(`/api/admin/products/current`),
  });
}

export function useProductVersions() {
  return useQuery({
    queryKey: ["products", "versions"],
    queryFn: () => apiGet<ProductView[]>(`/api/admin/products`),
  });
}

export function useCreateProductVersion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: NewProductVersion) =>
      apiSend<ProductView>("POST", `/api/admin/products`, v),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["products"] }),
  });
}
