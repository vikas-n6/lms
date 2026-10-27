// Phase 5 performance: application -> decision latency (phase prompt task 3).
// Target: decision p95 < 3 s at 50 concurrent applications.
//
// Runs the submit->decision leg against the gateway with a bearer token; measures the time from
// submit until the application leaves SUBMITTED (OFFERED / REJECTED / REFER). Authored for the
// Docker session; NOT executed here (no Docker) — record results in docs/perf/.
//
// Run (core+auth+apps up):
//   k6 run -e BASE=http://localhost:8080 -e KC=http://localhost:8180 scripts/perf/app-to-decision.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const KC = __ENV.KC || 'http://localhost:8180';
const decisionLatency = new Trend('decision_latency_ms', true);

export const options = {
  scenarios: {
    applications: {
      executor: 'constant-vus',
      vus: 50,
      duration: '60s',
    },
  },
  thresholds: {
    decision_latency_ms: ['p(95)<3000'],
    http_req_failed: ['rate<0.001'],
  },
};

function token() {
  const res = http.post(`${KC}/realms/lms/protocol/openid-connect/token`, {
    grant_type: 'password',
    client_id: 'gateway',
    client_secret: 'gateway-secret',
    username: 'admin',
    password: 'admin',
    scope: 'openid',
  });
  return res.json('access_token');
}

export function setup() {
  return { token: token() };
}

export default function (data) {
  const headers = {
    Authorization: `Bearer ${data.token}`,
    'Content-Type': 'application/json',
    'Idempotency-Key': `perf-${__VU}-${__ITER}-${Date.now()}`,
  };
  const phone = `9000${String(100000 + __VU * 1000 + __ITER).slice(-6)}`;
  const cust = http
    .post(`${BASE}/api/customers`, JSON.stringify({ fullName: 'Perf', phone, dateOfBirth: '1992-01-01' }), { headers })
    .json('id');
  if (!cust) return;
  http.post(`${BASE}/api/customers/${cust}/kyc`, JSON.stringify({ idNumber: 'ABCDE1234F' }), { headers });
  const app = http
    .post(
      `${BASE}/api/applications`,
      JSON.stringify({
        customerId: cust,
        productId: 'MICRO-V1',
        requestedAmountMinor: 3000000,
        phone,
        dateOfBirth: '1992-01-01',
        declaredMonthlyIncomeMinor: 5000000,
        employmentType: 'SALARIED',
        dataProcessingConsent: true,
        creditPullConsent: true,
        loanAgreementConsent: true,
      }),
      { headers },
    )
    .json('id');
  if (!app) return;

  const start = Date.now();
  http.post(`${BASE}/api/applications/${app}/submit`, null, { headers });
  let status = 'SUBMITTED';
  for (let i = 0; i < 30 && status === 'SUBMITTED'; i++) {
    status = http.get(`${BASE}/api/applications/${app}`, { headers }).json('status');
    if (status === 'SUBMITTED') sleep(0.1);
  }
  decisionLatency.add(Date.now() - start);
  check(status, { 'decision reached': (s) => s !== 'SUBMITTED' });
}
