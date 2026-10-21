alter table calendar_registration modify column user_id bigint null;

alter table calendar_registration add column kakao_provider_id varchar(100) null;

create unique index uk_calendar_registration_kakao_party_provider
    on calendar_registration (kakao_provider_id, party_id, provider);
