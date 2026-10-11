import { createContext, useContext, type ReactNode } from "react";
import { keycloak } from "./keycloak";

interface AuthContextValue {
  username: string;
  roles: string[];
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const value: AuthContextValue = {
    username: (keycloak.tokenParsed?.preferred_username as string) ?? "unknown",
    roles: (keycloak.tokenParsed?.realm_access?.roles as string[]) ?? [],
    logout: () => keycloak.logout({ redirectUri: window.location.origin }),
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return ctx;
}
