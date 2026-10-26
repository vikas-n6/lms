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
import { LoansPage } from "./pages/LoansPage";
import { LoanDetailPage } from "./pages/LoanDetailPage";
import { LedgerPage } from "./pages/LedgerPage";
import { DisbursementsPage } from "./pages/DisbursementsPage";
import { RepaymentsPage } from "./pages/RepaymentsPage";
import { MandatesPage } from "./pages/MandatesPage";
import { CollectionsPage } from "./pages/CollectionsPage";
import { CaseDetailPage } from "./pages/CaseDetailPage";
import { WriteOffsPage } from "./pages/WriteOffsPage";
import { NotificationsPage } from "./pages/NotificationsPage";
import { ReconciliationPage } from "./pages/ReconciliationPage";

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
          <Route path="/loans" element={<LoansPage />} />
          <Route path="/loans/:id" element={<LoanDetailPage />} />
          <Route path="/ledger" element={<LedgerPage />} />
          <Route path="/disbursements" element={<DisbursementsPage />} />
          <Route path="/repayments" element={<RepaymentsPage />} />
          <Route path="/mandates" element={<MandatesPage />} />
          <Route path="/collections" element={<CollectionsPage />} />
          <Route path="/collections/:id" element={<CaseDetailPage />} />
          <Route path="/write-offs" element={<WriteOffsPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/reconciliation" element={<ReconciliationPage />} />
          <Route path="/simulators" element={<SimulatorPage />} />
        </Routes>
      </AppShell>
    </ProtectedRoute>
  );
}
