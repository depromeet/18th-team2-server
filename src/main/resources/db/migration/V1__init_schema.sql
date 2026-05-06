create table avatar (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    name varchar(255) not null,
    primary key (id),
    constraint uk_avatar_name unique (name)
);

create table image (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    image_url varchar(255) not null,
    target_type varchar(50) not null,
    target_id bigint not null,
    sort_order integer not null,
    primary key (id),
    constraint uk_image_target_sort unique (target_type, target_id, sort_order)
);

create table rolling_paper_wrapper (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    name varchar(255) not null,
    primary key (id),
    constraint uk_rolling_paper_wrapper_name unique (name)
);

create table users (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    name varchar(255) not null,
    birth_day varchar(5) not null,
    provider varchar(100) not null,
    provider_id varchar(100) not null,
    email varchar(255) not null,
    primary key (id),
    constraint uk_users_provider_provider_id unique (provider, provider_id)
);

create table party (
    id bigint not null auto_increment,
    party_option varchar(31) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    owner_id bigint not null,
    name varchar(255),
    celebrant_nickname varchar(255),
    started_at datetime(6) not null,
    purpose varchar(255),
    primary key (id),
    constraint fk_party_owner foreign key (owner_id) references users (id)
);

create table paper_only_party (
    id bigint not null,
    primary key (id),
    constraint fk_paper_only_party_party foreign key (id) references party (id)
);

create table realtime_party (
    id bigint not null,
    primary key (id),
    constraint fk_realtime_party_party foreign key (id) references party (id)
);

create table participant (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    party_id bigint not null,
    user_id bigint,
    is_celebrant bit not null,
    has_written_paper bit not null,
    primary key (id),
    constraint uk_participant_party_user unique (party_id, user_id),
    constraint fk_participant_party foreign key (party_id) references party (id),
    constraint fk_participant_user foreign key (user_id) references users (id)
);

create table party_invite (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    party_id bigint not null,
    token varchar(16) not null,
    expires_at datetime(6) not null,
    primary key (id),
    constraint uk_party_invite_token unique (token),
    constraint fk_party_invite_party foreign key (party_id) references party (id)
);

create table realtime_participant_profile (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    participant_id bigint not null,
    nickname varchar(20) not null,
    character_id bigint,
    primary key (id),
    constraint uk_realtime_participant_profile_participant unique (participant_id),
    constraint fk_realtime_participant_profile_participant foreign key (participant_id) references participant (id),
    constraint fk_realtime_participant_profile_character foreign key (character_id) references avatar (id)
);

create table chat_message (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    content varchar(1000) not null,
    party_id bigint not null,
    participant_id bigint not null,
    primary key (id),
    constraint fk_chat_message_party foreign key (party_id) references party (id),
    constraint fk_chat_message_participant foreign key (participant_id) references participant (id)
);

create table rolling_paper (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    wrapper_id bigint not null,
    writer_participant_id bigint not null,
    party_id bigint not null,
    content varchar(100) not null,
    is_read bit not null,
    writer_nickname varchar(10) not null,
    writer_nickname_key varchar(10) not null,
    primary key (id),
    constraint uk_rolling_paper_party_writer_nickname unique (party_id, writer_nickname_key),
    constraint uk_rolling_paper_writer_participant unique (writer_participant_id),
    constraint fk_rolling_paper_wrapper foreign key (wrapper_id) references rolling_paper_wrapper (id),
    constraint fk_rolling_paper_writer_participant foreign key (writer_participant_id) references participant (id),
    constraint fk_rolling_paper_party foreign key (party_id) references party (id)
);
