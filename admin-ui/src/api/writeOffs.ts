import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiSend } from "./client";

export interface WriteOffRequest {
  id: string;
  loanId: string;
  loanNumber: string;
  reason: string;
  requestedBy: string;
  status: string;
  decidedBy: string | null;
  decisionNote: string | null;
  dpdAtRequest: number;
  createdAt: string;
  updatedAt: string;
}

export function useWriteOffRequests(status?: string) {
  const query = status ? `?status=${status}` : "";
  return useQuery({
    queryKey: ["writeOffRequests", status],
    queryFn: () => apiGet<WriteOffRequest[]>(`/api/write-off-requests${query}`),
  });
}

/** Approve/reject carry the acting admin via X-Acting-User so the two-person rule can be seen. */
function useDecision(path: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { requestId: string; actingUser: string; note?: string }) =>
      fetch(`/api/write-off-requests/${v.requestId}/${path}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Acting-User": v.actingUser,
        },
        body: v.note ? JSON.stringify({ note: v.note }) : undefined,
      }).then((r) => {
        if (!r.ok) throw new Error(`${r.status} ${r.statusText}`);
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["writeOffRequests"] }),
  });
}

export const useApproveWriteOff = () => useDecision("approve");
export const useRejectWriteOff = () => useDecision("reject");

export function useRequestWriteOff(loanId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (reason: string) =>
      apiSend("POST", `/api/loans/${loanId}/write-off-requests`, { reason }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["writeOffRequests"] }),
  });
}
