import { useQuery } from "@tanstack/react-query";
import { apiGet } from "./client";

export interface ServiceStatus {
  name: string;
  status: string;
}

export interface StatusReport {
  services: ServiceStatus[];
}

export function useStatus() {
  return useQuery({
    queryKey: ["status"],
    queryFn: () => apiGet<StatusReport>("/api/status"),
    refetchInterval: 10_000,
  });
}
