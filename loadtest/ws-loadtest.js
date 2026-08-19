import { WebSocket } from 'k6/experimental/websockets';
import { Trend, Rate } from 'k6/metrics';
import { seedFixtures } from './lib/fixtures.js';
import { encodeFrame, parseFrames, uuidv4 } from './lib/stomp.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/ws';
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

const enteredResponseTime = new Trend('ws_entered_response_time', true);
const connectSuccess = new Rate('ws_connect_success');

export function setup() {
  return seedFixtures(BASE_URL, PARTY_COUNT);
}

export default function (data) {
  const invite = data.invites[__VU % data.invites.length];
  const clientRequestId = uuidv4();
  const startedAt = Date.now();
  let entered = false;

  const ws = new WebSocket(WS_URL, ['v12.stomp']);

  ws.onopen = () => {
    ws.send(encodeFrame('CONNECT', { 'accept-version': '1.2', host: 'localhost' }, ''));
  };

  ws.onmessage = (msg) => {
    const frames = parseFrames(msg.data);
    for (const frame of frames) {
      if (frame.command === 'CONNECTED') {
        ws.send(
          encodeFrame(
            'SUBSCRIBE',
            { id: 'sub-personal', destination: `/topic/parties/${invite.partyId}/personal/${clientRequestId}` },
            '',
          ),
        );
        ws.send(
          encodeFrame(
            'SEND',
            {
              destination: `/app/party-invites/${invite.token}/realtime-participants`,
              'content-type': 'application/json',
            },
            JSON.stringify({
              nickname: `u${__VU}-${__ITER}`,
              characterId: data.characterId,
              clientRequestId,
            }),
          ),
        );
      } else if (frame.command === 'MESSAGE' && !entered) {
        const body = JSON.parse(frame.body);
        if (body.event === 'entered') {
          entered = true;
          connectSuccess.add(true);
          enteredResponseTime.add(Date.now() - startedAt);
          // 브로드캐스트 토픽 구독은 입장 성공 이후에만 인가된다(StompDestinationAuthorizationInterceptor).
          ws.send(
            encodeFrame(
              'SUBSCRIBE',
              { id: 'sub-broadcast', destination: `/topic/parties/${invite.partyId}` },
              '',
            ),
          );
          setTimeout(() => ws.close(), HOLD_SECONDS * 1000);
        }
      } else if (frame.command === 'ERROR') {
        if (!entered) connectSuccess.add(false);
      }
    }
  };

  ws.onerror = () => {
    if (!entered) connectSuccess.add(false);
  };
}
