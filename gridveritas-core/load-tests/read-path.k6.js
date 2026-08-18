// k6 load test for gridveritas-core's read/auth path: token issuance, sources
// list, and the audit assistant's read-only endpoints. Deliberately does NOT
// cover attestation ingest or verify-by-payload-hash - those need a real
// Ed25519 signature per request, which k6's JS runtime has no native support
// for. Use IngestLoadRunner.java (../src/test/java/.../loadtest/) for that half.
//
// Run:
//   k6 run -e BASE_URL=http://localhost:8080 \
//          -e ADMIN_PASSWORD=admin-change-me \
//          read-path.k6.js
//
// Tune load with k6's own flags, e.g.:
//   k6 run --vus 20 --duration 30s -e BASE_URL=... -e ADMIN_PASSWORD=... read-path.k6.js
//
// NEVER point this at a production instance without the operator's explicit
// go-ahead - it generates real load and will trip the rate limiter by design
// once you push past gridveritas.security.rate-limit.* thresholds.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const throttled = new Rate('throttled_429');
const authLatency = new Trend('auth_token_latency', true);

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '15s',
  thresholds: {
    // Document expectations, don't just eyeball the summary: a real 5xx rate
    // above 1% or an auth call regularly over a second means something's wrong,
    // whether that's this run's target or a regression to chase down later.
    http_req_failed: ['rate<0.01'],
    auth_token_latency: ['p(95)<1000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD;

if (!ADMIN_PASSWORD) {
  throw new Error('Set -e ADMIN_PASSWORD=<gridveritas.security.users.admin-password>');
}

export default function () {
  const tokenRes = http.post(
    `${BASE_URL}/api/v1/auth/token`,
    JSON.stringify({ username: 'admin', password: ADMIN_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'auth_token' } }
  );
  authLatency.add(tokenRes.timings.duration);
  throttled.add(tokenRes.status === 429);

  check(tokenRes, {
    'auth: 200 or 429 (never 5xx)': (r) => r.status === 200 || r.status === 429,
  });

  if (tokenRes.status !== 200) {
    sleep(1); // back off instead of hammering an already-throttling server
    return;
  }

  const token = tokenRes.json('token');
  const authHeaders = { headers: { Authorization: `Bearer ${token}` } };

  const sourcesRes = http.get(`${BASE_URL}/api/v1/sources`, { ...authHeaders, tags: { name: 'list_sources' } });
  throttled.add(sourcesRes.status === 429);
  check(sourcesRes, { 'sources: 200 or 429': (r) => r.status === 200 || r.status === 429 });

  const auditRes = http.get(`${BASE_URL}/api/v1/audit`, { ...authHeaders, tags: { name: 'list_audit' } });
  throttled.add(auditRes.status === 429);
  check(auditRes, { 'audit: 200 or 429': (r) => r.status === 200 || r.status === 429 });

  const healthRes = http.get(`${BASE_URL}/actuator/health`, { tags: { name: 'health' } });
  check(healthRes, { 'health: 200': (r) => r.status === 200 });

  sleep(0.2);
}
