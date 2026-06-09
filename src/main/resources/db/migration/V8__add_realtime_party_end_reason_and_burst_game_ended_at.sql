ALTER TABLE realtime_party
    ADD COLUMN live_ending_reason VARCHAR(50) NULL,
    ADD COLUMN burst_game_ended_at DATETIME(6) NULL;

UPDATE realtime_party realtime_party
JOIN party party ON party.id = realtime_party.id
SET realtime_party.live_ending_reason =
    CASE
        WHEN realtime_party.live_ending_started_at < DATE_ADD(party.started_at, INTERVAL 10 MINUTE)
            THEN 'HOST_REQUEST'
        ELSE 'TIME_LIMIT_REACHED'
    END
WHERE realtime_party.live_ending_started_at IS NOT NULL
  AND realtime_party.live_ending_reason IS NULL;
