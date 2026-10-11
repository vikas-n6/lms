import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import { initKeycloak } from "./auth/keycloak";
import { AuthProvider } from "./auth/AuthProvider";
import App from "./App";
import "./index.css";

const queryClient = new QueryClient();

// Authenticate (Authorization Code + PKCE) before rendering; onLoad=login-required redirects.
initKeycloak().then((authenticated) => {
  const root = createRoot(document.getElementById("root")!);
  if (!authenticated) {
    root.render(<div style={{ padding: 24 }}>Redirecting to sign in…</div>);
    return;
  }
  root.render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </AuthProvider>
      </QueryClientProvider>
    </StrictMode>,
  );
});
