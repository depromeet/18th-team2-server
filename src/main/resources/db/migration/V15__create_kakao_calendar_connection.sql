create table kakao_calendar_connection (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    user_id bigint not null,
    access_token varchar(1024) not null,
    refresh_token varchar(1024) not null,
    access_token_expires_at datetime(6) not null,
    refresh_token_expires_at datetime(6) not null,
    primary key (id),
    constraint uk_kakao_calendar_connection_user unique (user_id)
);
