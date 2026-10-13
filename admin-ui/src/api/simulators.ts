import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiSend } from "./client";

export interface SimConfig {
  latencyMsMin: number;
  latencyMsMax: number;
  failureRate: number;
  timeoutRate: number;
  duplicateCallbackRate: number;
  callbackDelayMs: number;
  seed: number;
}

export interface SmsMessage {
  id: string;
  to: string;
  body: string;
  sentAt: string;
}

export function useSimConfigs() {
  return useQuery({
    queryKey: ["sim-config"],
    queryFn: () => apiGet<Record<string, SimConfig>>("/api/sim/config"),
    refetchInterval: 5_000,
  });
}

export function useSmsOutbox() {
  return useQuery({
    queryKey: ["sms-outbox"],
    queryFn: () => apiGet<SmsMessage[]>("/api/sim/sms/outbox"),
    refetchInterval: 3_000,
  });
}

export function useSimActions() {
  const qc = useQueryClient();
  const invalidate = () => qc.invalidateQueries({ queryKey: ["sim-config"] });
  return {
    putConfig: useMutation({
      mutationFn: (v: { simulator: string; config: SimConfig }) =>
        apiSend("PUT", `/api/sim/config/${v.simulator}`, v.config),
      onSuccess: invalidate,
    }),
    applyPresetAll: useMutation({
      mutationFn: (preset: string) => apiSend("POST", `/api/sim/preset/${preset}?seed=1`),
      onSuccess: invalidate,
    }),
    reset: useMutation({ mutationFn: () => apiSend("POST", "/api/sim/reset"), onSuccess: invalidate }),
  };
}
