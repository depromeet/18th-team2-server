import sse from 'k6/x/sse';
import { sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { seedFixtures } from './lib/fixtures.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PARTY_COUNT = parseInt(__ENV.PARTY_COUNT || '80', 10);
const HOLD_SECONDS = parseInt(__ENV.HOLD_SECONDS || '20', 10);
const VUS = parseInt(__ENV.VUS || '50', 10);

export const options = {
  scenarios: {
    stage: {
      executor: 'constant-vus',
      vus: VUS,
      duration: `${HOLD_SECONDS + 15}s`,
    },
  },
  setupTimeout: '180s',
};

const enteredResponseTime = new Trend('sse_entered_response_time', true);
const connectSuccess = new Rate('sse_connect_success');

export function setup() {
  return seedFixtures(BASE_URL, PARTY_COUNT);
}

export default function (data) {
  const invite = data.invites[__VU % data.invites.length];
  const url = `${BASE_URL}/api/v1/party-invites/${invite.token}/realtime-participants/stream`;
  const params = {
    method: 'POST',
    body: JSON.stringify({ nickname: `u${__VU}-${__ITER}`, characterId: data.characterId }),
    headers: { 'Content-Type': 'application/json' },
  };
  const startedAt = Date.now();
  let entered = false;

  sse.open(url, params, function (client) {
    client.on('event', function (event) {
      if (event.name === 'entered' && !entered) {
        entered = true;
        connectSuccess.add(true);
        enteredResponseTime.add(Date.now() - startedAt);
        sleep(HOLD_SECONDS);
        client.close();
      }
    });
    client.on('error', function () {
      if (!entered) connectSuccess.add(false);
    });
  });
}
