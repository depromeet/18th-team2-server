ALTER TABLE realtime_party
    ADD COLUMN live_started_at DATETIME(6) NULL;

UPDATE realtime_party realtime_party
    JOIN party party ON party.id = realtime_party.id
SET realtime_party.live_started_at = COALESCE(realtime_party.host_entered_at, party.started_at)
WHERE party.started_at <= NOW();
