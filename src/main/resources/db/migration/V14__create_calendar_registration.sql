create table calendar_registration (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    user_id bigint not null,
    party_id bigint not null,
    provider varchar(20) not null,
    event_id varchar(100) null,
    primary key (id),
    constraint uk_calendar_registration_user_party_provider unique (user_id, party_id, provider)
);
