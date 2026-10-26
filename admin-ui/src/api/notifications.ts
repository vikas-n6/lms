import { useQuery } from "@tanstack/react-query";
import { apiGet } from "./client";

export interface NotificationRow {
  id: string;
  triggerEventId: string;
  templateId: string;
  channel: string;
  status: string;
  attempts: number;
  failureCode: string | null;
  createdAt: string;
}

export function useNotificationLog(status?: string) {
  const query = status ? `?status=${status}` : "";
  return useQuery({
    queryKey: ["notificationLog", status],
    queryFn: () => apiGet<NotificationRow[]>(`/api/notifications/log${query}`),
  });
}
