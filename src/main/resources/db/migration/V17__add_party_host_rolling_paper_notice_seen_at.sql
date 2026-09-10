ALTER TABLE party
    ADD COLUMN host_rolling_paper_notice_seen_at datetime(6) NULL;

-- 기존 파티 백필.
-- 이미 주최자 열람이 열린 파티는 안내를 소진한 것으로 본다. 배포 직후 이미 롤링페이퍼를
-- 확인한 주최자에게 뒤늦은 안내가 뜨는 것을 막는다.
-- 아직 열리지 않은 파티는 NULL 로 남겨 정상적으로 안내를 받게 한다.

-- PAPER_ONLY: 시작일 22시. 시작 시각이 그보다 늦으면 다음날 22시.
UPDATE party
SET host_rolling_paper_notice_seen_at = NOW(6)
WHERE party_option = 'PAPER_ONLY'
  AND NOW(6) >= (
      CASE
          WHEN started_at < DATE_ADD(DATE(started_at), INTERVAL 22 HOUR)
              THEN DATE_ADD(DATE(started_at), INTERVAL 22 HOUR)
          ELSE DATE_ADD(DATE(started_at), INTERVAL 46 HOUR)
      END
  );

-- REALTIME: 조기 종료 시각 → 실제 시작 + 10분 → 예약 시작 + 30분(시작 유예 마감) 순으로 적용.
UPDATE party
    JOIN realtime_party ON realtime_party.id = party.id
SET party.host_rolling_paper_notice_seen_at = NOW(6)
WHERE party.party_option = 'REALTIME'
  AND NOW(6) >= COALESCE(
      realtime_party.live_ending_started_at,
      DATE_ADD(realtime_party.live_started_at, INTERVAL 10 MINUTE),
      DATE_ADD(party.started_at, INTERVAL 30 MINUTE)
  );
