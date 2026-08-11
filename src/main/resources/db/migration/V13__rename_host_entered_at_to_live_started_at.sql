ALTER TABLE realtime_party
    RENAME COLUMN host_entered_at TO live_started_at;

UPDATE realtime_party realtime_party
    JOIN party party ON party.id = realtime_party.id
SET realtime_party.live_started_at = party.started_at
WHERE party.started_at <= NOW();
