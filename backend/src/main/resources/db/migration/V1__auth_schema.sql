create table users (
    id uuid primary key,
    google_subject varchar(255) not null unique,
    email varchar(320) not null,
    nickname varchar(20),
    profile_image_url varchar(2048),
    status varchar(20) not null,
    role varchar(20) not null,
    onboarding_completed boolean not null default false,
    terms_version varchar(50),
    locale varchar(10) not null default 'ko-KR',
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table user_devices (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    device_identifier varchar(255) not null,
    platform varchar(20) not null,
    fcm_token varchar(4096),
    app_version varchar(100),
    updated_at timestamp with time zone not null,
    unique (user_id, device_identifier)
);

create table refresh_tokens (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    device_id uuid not null references user_devices(id) on delete cascade,
    token_hash varchar(64) not null unique,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone not null
);

create index idx_refresh_tokens_user_id on refresh_tokens(user_id);
