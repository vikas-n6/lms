import { Routes, Route } from "react-router-dom";
import { ProtectedRoute } from "./auth/ProtectedRoute";
import { AppShell } from "./components/AppShell";
import { StatusPage } from "./pages/StatusPage";

export default function App() {
  return (
    <ProtectedRoute>
      <AppShell>
        <Routes>
          <Route path="/" element={<StatusPage />} />
        </Routes>
      </AppShell>
    </ProtectedRoute>
  );
}
