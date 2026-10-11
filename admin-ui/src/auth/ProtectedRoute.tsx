import { type ReactNode } from "react";
import { keycloak } from "./keycloak";

/** Renders children only when authenticated; otherwise kicks off the login redirect. */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  if (!keycloak.authenticated) {
    keycloak.login();
    return <div style={{ padding: 24 }}>Redirecting to sign in…</div>;
  }
  return <>{children}</>;
}
