// Phase 3 payments-callback latency check (informational). Posts signed payment callbacks at
// ~500 req/s and asserts p95 acknowledgement < 200 ms (phase acceptance test, informational).
//
// The callback body must be signed exactly as lms-security CallbackVerifier expects:
//   header X-Callback-Signature: t=<epochMillis>,v1=<base64(HMAC-SHA256(secret, epochMillis + "." + body))>
// k6 computes the HMAC with the shared secret (LMS_CALLBACK_SECRET).
//
// Run (Docker session, with core+apps up):
//   k6 run -e BASE=http://localhost:8080 -e SECRET=local-dev-callback-secret \
//          scripts/k6/phase3-callback-latency.js
//
// Authored for the Docker session; not executed here. Informational in Phase 3.
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
      maxVUs: 300,
    },
  },
  thresholds: {
    callback_ack_ms: ['p(95)<200'],
    http_req_failed: ['rate<0.01'],
  },
};

function sign(body) {
  const t = Date.now();
  const mac = crypto.hmac('sha256', SECRET, `${t}.${body}`, 'binary');
  const v1 = encoding.b64encode(mac);
  return `t=${t},v1=${v1}`;
}

export default function () {
  const loanId = '018f9a00-0000-7000-8000-0000000000aa';
  const linkId = `PL-${__VU}-${__ITER}`;
  const body = JSON.stringify({
    linkId,
    loanId,
    amountMinor: 474000,
    currency: 'INR',
    status: 'SUCCESS',
  });
  const res = http.post(`${BASE}/callbacks/payments`, body, {
    headers: { 'Content-Type': 'application/json', 'X-Callback-Signature': sign(body) },
  });
  ackLatency.add(res.timings.duration);
  check(res, { 'acked 2xx': (r) => r.status >= 200 && r.status < 300 });
}
