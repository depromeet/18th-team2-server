-- realtime_participant_profile에 participant_token 추가
ALTER TABLE realtime_participant_profile
    ADD COLUMN participant_token VARCHAR(8) NOT NULL DEFAULT '';

UPDATE realtime_participant_profile
    SET participant_token = LPAD(LOWER(CONV(id, 10, 36)), 8, '0');

ALTER TABLE realtime_participant_profile
    MODIFY COLUMN participant_token VARCHAR(8) NOT NULL,
    ADD CONSTRAINT uk_realtime_participant_profile_token UNIQUE (participant_token);

-- chat_message: participant_id 제거 후 profile_id 추가
TRUNCATE TABLE chat_message;

ALTER TABLE chat_message
    DROP FOREIGN KEY fk_chat_message_participant,
    DROP COLUMN participant_id,
    ADD COLUMN profile_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_chat_message_profile FOREIGN KEY (profile_id) REFERENCES realtime_participant_profile (id);
