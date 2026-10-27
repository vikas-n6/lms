import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { keycloak } from "../auth/keycloak";

/**
 * Subscribes to the gateway's dashboard SSE stream and invalidates the reporting queries whenever a
 * change is pushed, so the dashboards update live (docs/architecture.md: Redis pub/sub fanned out to
 * SSE by the gateway). EventSource cannot send an Authorization header, so we read the stream with
 * fetch + a bearer token. The queries also poll on an interval, so if the stream is unavailable the
 * dashboards still refresh — the stream just makes updates immediate.
 */
export function useDashboardStream() {
  const qc = useQueryClient();
  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false;

    async function connect() {
      try {
        await keycloak.updateToken(30).catch(() => undefined);
        const response = await fetch("/api/stream/dashboard", {
          headers: { Authorization: `Bearer ${keycloak.token ?? ""}`, Accept: "text/event-stream" },
          signal: controller.signal,
        });
        if (!response.ok || !response.body) return;
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        while (!cancelled) {
          const { value, done } = await reader.read();
          if (done) break;
          const chunk = decoder.decode(value);
          // Any "dashboard" event means the read models moved; refresh the reporting queries.
          if (chunk.includes("event:dashboard") || chunk.includes("event: dashboard")) {
            void qc.invalidateQueries({ queryKey: ["reporting"] });
          }
        }
      } catch {
        // Stream unavailable (or aborted): polling keeps the dashboards fresh.
      }
    }

    void connect();
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [qc]);
}
