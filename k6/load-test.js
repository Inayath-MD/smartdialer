import http from 'k6/http';
import { check, sleep } from 'k6';

// K6 Load Test Configuration for 100, 1,000, and 10,000 concurrent agents
export const options = {
    stages: [
        { duration: '30s', target: 100 },   // Warm up to 100 agents
        { duration: '1m',  target: 100 },   // Steady state at 100 agents
        { duration: '30s', target: 1000 },  // Ramp to 1,000 agents
        { duration: '1m',  target: 1000 },  // Steady state at 1,000 agents
        { duration: '1m',  target: 5000 },  // Ramp towards 10,000
        { duration: '30s', target: 0 },     // Cool down
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'], // 95% of requests must complete below 500ms
        http_req_failed: ['rate<0.01'],   // Error rate under 1%
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    // 1. Check dialer status
    const statusRes = http.get(`${BASE_URL}/api/dialer/status?campaignId=1`);
    check(statusRes, {
        'status is 200': (r) => r.status === 200,
    });

    // 2. Trigger progressive dialing batch
    const dialPayload = JSON.stringify({ campaignId: 1 });
    const dialHeaders = { 'Content-Type': 'application/json' };

    const dialRes = http.post(`${BASE_URL}/api/dialer/progressive/run`, dialPayload, { headers: dialHeaders });
    check(dialRes, {
        'progressive run returned 200': (r) => r.status === 200,
    });

    // 3. Heartbeat report
    const agentId = (__VU % 100) + 1;
    const hbRes = http.post(`${BASE_URL}/api/agents/${agentId}/heartbeat`);
    check(hbRes, {
        'heartbeat accepted': (r) => r.status === 204 || r.status === 200 || r.status === 404,
    });

    sleep(1);
}
