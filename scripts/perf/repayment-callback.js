// Phase 5 performance: repayment callback acknowledgement latency (phase prompt task 3).
// Target: callback ack p95 < 200 ms at 500 req/s.
//
// Posts signed payments callbacks to the gateway (which routes /callbacks/** straight through, no
// JWT; the service verifies the HMAC). Signature format (lms-security CallbackVerifier):
//   X-Callback-Signature: t=<epochMillis>,v1=<base64(HMAC-SHA256(secret, epochMillis + "." + body))>
// This mirrors scripts/k6/phase3-callback-latency.js; kept under scripts/perf/ for the Phase 5 run.
// Authored for the Docker session; NOT executed here. Record results in docs/perf/.
//
// Run: k6 run -e BASE=http://localhost:8080 -e SECRET=local-dev-callback-secret scripts/perf/repayment-callback.js
import http from 'k6/http';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const SECRET = __ENV.SECRET || 'local-dev-callback-secret';
const ackLatency = new Trend('callback_ack_ms', true);

export const options = {
  scenarios: {
    callbacks: {
      executor: 'constant-arrival-rate',
      rate: 500,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 100,
      maxVUs: 400,
    },
  },
  thresholds: {
    callback_ack_ms: ['p(95)<200'],
    http_req_failed: ['rate<0.001'],
  },
};

function sign(body) {
  const t = Date.now().toString();
  const mac = crypto.hmac('sha256', SECRET, `${t}.${body}`, 'binary');
  return `t=${t},v1=${encoding.b64encode(mac)}`;
}

export default function () {
  const ref = `PERF-${__VU}-${__ITER}-${Date.now()}`;
  const body = JSON.stringify({ providerReference: ref, status: 'SUCCESS', amountMinor: 100000, currency: 'INR' });
  const res = http.post(`${BASE}/callbacks/payments`, body, {
    headers: { 'Content-Type': 'application/json', 'X-Callback-Signature': sign(body) },
  });
  ackLatency.add(res.timings.duration);
  check(res, { 'ack 2xx': (r) => r.status >= 200 && r.status < 300 });
}
