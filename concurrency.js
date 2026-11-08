import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

http.setResponseCallback(http.expectedStatuses(200, 409));
const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const ACTIVITY_ID = __ENV.ACTIVITY_ID;
const USERS = Number(__ENV.USERS || 100);
const RUN_ID = __ENV.RUN_ID || Date.now().toString(36);

const ok = new Counter('register_ok');
const conflict = new Counter('register_conflict');
const other = new Counter('register_other');
const latency = new Trend('register_latency', true);

export const options = {
    setupTimeout: '300s',
    thresholds: {
        register_other: ['count==0'],
        register_ok: ['count==10'],
    },
};

export function setup() {
    const tokens = [];
    for (let i = 0; i < USERS; i++) {
        const username = `lt${RUN_ID}${i}`;
        const res = http.post(`${BASE}/api/auth/register`,
            JSON.stringify({ username, password: 'Passw0rd!', displayName: username }),
            { headers: { 'Content-Type': 'application/json' } });
        if (res.status !== 200) throw new Error(`signup ${i} failed: ${res.status} ${res.body}`);
        tokens.push(res.json('data.token'));
    }
    return { tokens };
}

export default function (data) {
    const token = data.tokens[(__VU - 1) % data.tokens.length];
    const started = Date.now();
    const res = http.post(`${BASE}/api/registration/${ACTIVITY_ID}/register`, null,
        { headers: { Authorization: `Bearer ${token}` } });
    latency.add(Date.now() - started);

    if (res.status === 200) ok.add(1);
    else if (res.status === 409) conflict.add(1);
    else { other.add(1); console.error(`vu=${__VU} status=${res.status} body=${res.body}`); }

    check(res, { 'accepted or rejected': (r) => r.status === 200 || r.status === 409 });
}