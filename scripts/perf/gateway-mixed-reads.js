// Phase 5 performance: mixed read traffic through the gateway (phase prompt task 3).
// Target: gateway error rate < 0.1% under a mixed read load.
//
// Hits the common back-office read endpoints (loans, ledger trial balance, reporting dashboards,
// collections) with a bearer token, weighted like real dashboard usage. Authored for the Docker
// session; NOT executed here. Record results in docs/perf/.
//
// Run: k6 run -e BASE=http://localhost:8080 -e KC=http://localhost:8180 scripts/perf/gateway-mixed-reads.js
import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const KC = __ENV.KC || 'http://localhost:8180';
const errors = new Rate('gateway_errors');

export const options = {
  scenarios: {
    reads: { executor: 'constant-arrival-rate', rate: 200, timeUnit: '1s', duration: '60s', preAllocatedVUs: 50, maxVUs: 200 },
  },
  thresholds: {
    gateway_errors: ['rate<0.001'],
    http_req_failed: ['rate<0.001'],
    http_req_duration: ['p(95)<1000'],
  },
};

const PATHS = [
  '/api/reporting/portfolio',
  '/api/reporting/funnel',
  '/api/reporting/collection-efficiency',
  '/api/reporting/dpd-distribution',
  '/api/ledger/trial-balance',
  '/api/loans',
  '/api/collections/cases?status=OPEN',
];

function token() {
  return http
    .post(`${KC}/realms/lms/protocol/openid-connect/token`, {
      grant_type: 'password',
      client_id: 'gateway',
      client_secret: 'gateway-secret',
      username: 'admin',
      password: 'admin',
      scope: 'openid',
    })
    .json('access_token');
}

export function setup() {
  return { token: token() };
}

export default function (data) {
  const path = PATHS[Math.floor(Math.random() * PATHS.length)];
  const res = http.get(`${BASE}${path}`, { headers: { Authorization: `Bearer ${data.token}` } });
  const ok = res.status >= 200 && res.status < 400;
  errors.add(!ok);
  check(res, { 'read ok': () => ok });
}
