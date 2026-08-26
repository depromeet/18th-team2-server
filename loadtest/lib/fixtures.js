import http from 'k6/http';
import { check } from 'k6';

const DEV_EMAIL = 'loadtest@team2.local';

export function seedFixtures(baseUrl, partyCount) {
  const tokenRes = http.post(`${baseUrl}/api/dev/token?email=${DEV_EMAIL}`);
  check(tokenRes, { 'dev token issued': (r) => r.status === 200 });
  if (tokenRes.status !== 200) {
    throw new Error(`dev token issue failed: ${tokenRes.status} ${tokenRes.body}`);
  }
  const jwt = tokenRes.json('data').token;
  const authHeaders = {
    headers: { Authorization: `Bearer ${jwt}`, 'Content-Type': 'application/json' },
  };

  // 서버는 Asia/Seoul(KST, UTC+9) 로컬 시각의 LocalDate/LocalTime을 그대로 받아 비교한다.
  // toISOString()은 항상 UTC 기준이므로 KST로 보정한 시각의 UTC 표현을 사용해
  // 실행 환경의 시스템 타임존과 무관하게 KST 기준 "1분 전"을 얻는다.
  const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
  const now = new Date(Date.now() - 60 * 1000 + KST_OFFSET_MS); // KST 기준 1분 전 시작 -> LIVE_OPEN 보장
  const startedDate = now.toISOString().slice(0, 10);
  const startTime = now.toISOString().slice(11, 16);

  const invites = [];
  for (let i = 0; i < partyCount; i++) {
    const createRes = http.post(
      `${baseUrl}/api/v1/parties/realtime`,
      JSON.stringify({
        celebrantNickname: `LoadTestHost${i}`,
        startedDate,
        startTime,
        characterId: 1,
      }),
      authHeaders,
    );
    if (createRes.status !== 201) {
      throw new Error(`party create failed [${i}]: ${createRes.status} ${createRes.body}`);
    }
    const partyId = createRes.json('data').partyId;

    const inviteRes = http.post(
      `${baseUrl}/api/v1/parties/${partyId}/invite-link`,
      null,
      authHeaders,
    );
    if (inviteRes.status !== 200) {
      throw new Error(`invite create failed [${i}]: ${inviteRes.status} ${inviteRes.body}`);
    }
    invites.push({ partyId, token: inviteRes.json('data').token });
  }

  return { invites, characterId: 1 };
}
