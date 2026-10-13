// Phase 1 decision-latency check (informational). Measures submit -> decisioned round-trip at
// concurrency, asserting p95 < 3s under the simulator "healthy" preset (phase acceptance test).
//
// Run (Docker session, with core+auth+apps up and a token):
//   k6 run -e TOKEN=$TOKEN -e GW=http://localhost:8080 scripts/k6/phase1-decision-latency.js
//
// Authored for the Docker session; not executed here. This is informational in Phase 1.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const GW = __ENV.GW || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';

const decisionLatency = new Trend('decision_latency_ms', true);

export const options = {
  scenarios: {
    concurrent_applications: {
      executor: 'per-vu-iterations',
      vus: 20,
      iterations: 5,
      maxDuration: '3m',
    },
  },
  thresholds: {
    // Informational in Phase 1, but encode the target.
    decision_latency_ms: ['p(95)<3000'],
  },
};

function headers() {
  return {
    headers: {
      Authorization: `Bearer ${TOKEN}`,
      'Content-Type': 'application/json',
      'Idempotency-Key': `k6-${__VU}-${__ITER}-${Date.now()}`,
    },
  };
}

export default function () {
  const phone = `9000${String(100000 + __VU * 100 + __ITER)}`;
  // Register + a minimal journey (KYC/bank/consents) so submit is accepted.
  const cust = http.post(`${GW}/api/customers`,
    JSON.stringify({ fullName: 'K6 User', phone, dateOfBirth: '1992-01-01' }), headers());
  check(cust, { 'registered': (r) => r.status === 201 });
  const customerId = cust.json('id');

  http.post(`${GW}/api/customers/${customerId}/kyc`, JSON.stringify({ idNumber: 'ABCDE1234F' }), headers());
  http.post(`${GW}/api/customers/${customerId}/bank-accounts`,
    JSON.stringify({ accountNumber: '123456789012', ifsc: 'HDFC0001' }), headers());
  for (const type of ['DATA_PROCESSING', 'CREDIT_PULL', 'LOAN_AGREEMENT']) {
    http.post(`${GW}/api/customers/${customerId}/consents`, JSON.stringify({ type }), headers());
  }

  const app = http.post(`${GW}/api/applications`, JSON.stringify({
    customerId, productId: 'MICRO-V1', requestedAmountMinor: 3000000, phone,
    dateOfBirth: '1992-01-01', declaredMonthlyIncomeMinor: 5000000, employmentType: 'SALARIED',
    dataProcessingConsent: true, creditPullConsent: true, loanAgreementConsent: true,
  }), headers());
  const appId = app.json('id');

  const start = Date.now();
  http.post(`${GW}/api/applications/${appId}/submit`, null, headers());

  // Poll until the application leaves SUBMITTED (decision arrived).
  let status = 'SUBMITTED';
  for (let i = 0; i < 60 && (status === 'SUBMITTED' || status === 'DRAFT'); i++) {
    const r = http.get(`${GW}/api/applications/${appId}`, headers());
    status = r.json('status');
    if (status !== 'SUBMITTED' && status !== 'DRAFT') break;
    sleep(0.25);
  }
  decisionLatency.add(Date.now() - start);
  check(null, { 'reached a decision': () => status !== 'SUBMITTED' && status !== 'DRAFT' });
}
