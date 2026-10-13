import { Routes, Route } from "react-router-dom";
import { ProtectedRoute } from "./auth/ProtectedRoute";
import { AppShell } from "./components/AppShell";
import { StatusPage } from "./pages/StatusPage";
import { ApplicationsPage } from "./pages/ApplicationsPage";
import { ApplicationDetailPage } from "./pages/ApplicationDetailPage";
import { NewApplicationPage } from "./pages/NewApplicationPage";
import { DecisionExplainerPage } from "./pages/DecisionExplainerPage";
import { ReviewQueuePage } from "./pages/ReviewQueuePage";
import { SimulatorPage } from "./pages/SimulatorPage";
import { CustomerPage } from "./pages/CustomerPage";

export default function App() {
  return (
    <ProtectedRoute>
      <AppShell>
        <Routes>
          <Route path="/" element={<StatusPage />} />
          <Route path="/customers" element={<CustomerPage />} />
          <Route path="/applications" element={<ApplicationsPage />} />
          <Route path="/applications/new" element={<NewApplicationPage />} />
          <Route path="/applications/:id" element={<ApplicationDetailPage />} />
          <Route path="/decisions" element={<DecisionExplainerPage />} />
          <Route path="/decisions/:id" element={<DecisionExplainerPage />} />
          <Route path="/review-queue" element={<ReviewQueuePage />} />
          <Route path="/simulators" element={<SimulatorPage />} />
        </Routes>
      </AppShell>
    </ProtectedRoute>
  );
}
