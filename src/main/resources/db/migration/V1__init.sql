-- TripMate V1: all tables extend BaseEntity audit columns
-- is_deleted INT, remarks VARCHAR(150), created_at TIMESTAMPTZ, created_by BIGINT,
-- updated_at TIMESTAMPTZ, updated_by BIGINT, is_active SMALLINT

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    profile_image TEXT,
    mobile VARCHAR(20),
    google_sub VARCHAR(255) UNIQUE,
    refresh_token_hash VARCHAR(500),
    is_deleted INT NOT NULL DEFAULT 0,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    updated_by BIGINT,
    is_active SMALLINT NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_alive ON users(is_deleted, is_active);

CREATE TABLE IF NOT EXISTS trips (
    id BIGSERIAL PRIMARY KEY,
    trip_name VARCHAR(255) NOT NULL,
    start_name VARCHAR(255),
    start_lat DOUBLE PRECISION,
    start_lng DOUBLE PRECISION,
    dest_name VARCHAR(255),
    dest_lat DOUBLE PRECISION,
    dest_lng DOUBLE PRECISION,
    start_date DATE,
    end_date DATE,
    days_count INT,
    created_by_user_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNING',
    invite_code VARCHAR(12) UNIQUE,
    route_geojson TEXT,
    total_km DOUBLE PRECISION,
    is_deleted INT NOT NULL DEFAULT 0,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    updated_by BIGINT,
    is_active SMALLINT NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_trips_invite ON trips(invite_code);
CREATE INDEX IF NOT EXISTS idx_trips_alive ON trips(is_deleted, is_active);

CREATE TABLE IF NOT EXISTS trip_members (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(10) NOT NULL DEFAULT 'MEMBER',
    rsvp VARCHAR(12) DEFAULT 'GOING',
    is_deleted INT NOT NULL DEFAULT 0,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    updated_by BIGINT,
    is_active SMALLINT NOT NULL DEFAULT 1,
    CONSTRAINT uq_member_trip_user UNIQUE (trip_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_members_trip ON trip_members(trip_id);

CREATE TABLE IF NOT EXISTS places (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    address TEXT,
    image_url TEXT,
    description TEXT,
    category VARCHAR(50),
    rating DOUBLE PRECISION,
    added_by BIGINT,
    is_deleted INT NOT NULL DEFAULT 0,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    updated_by BIGINT,
    is_active SMALLINT NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_places_trip ON places(trip_id);

CREATE TABLE IF NOT EXISTS place_votes (
    id BIGSERIAL PRIMARY KEY,
    place_id BIGINT NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vote_status VARCHAR(10) NOT NULL,
    is_deleted INT NOT NULL DEFAULT 0,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    updated_by BIGINT,
    is_active SMALLINT NOT NULL DEFAULT 1,
    CONSTRAINT uq_vote_place_user UNIQUE (place_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_votes_place ON place_votes(place_id);

CREATE TABLE IF NOT EXISTS comments (
    id BIGSERIAL PRIMARY KEY,
    place_id BIGINT NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    message TEXT NOT NULL,
    is_deleted INT NOT NULL DEFAULT 0,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    updated_by BIGINT,
    is_active SMALLINT NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_comments_place ON comments(place_id);

CREATE TABLE IF NOT EXISTS itinerary_items (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    day_no INT NOT NULL,
    place_id BIGINT REFERENCES places(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    distance_km DOUBLE PRECISION,
    drive_mins INT,
    stay_notes TEXT,
    sort_order INT DEFAULT 0,
    is_deleted INT NOT NULL DEFAULT 0,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    updated_by BIGINT,
    is_active SMALLINT NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_itin_trip ON itinerary_items(trip_id, day_no);

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    sender_id BIGINT NOT NULL REFERENCES users(id),
    content TEXT,
    image_url TEXT,
    shared_lat DOUBLE PRECISION,
    shared_lng DOUBLE PRECISION,
    is_deleted INT NOT NULL DEFAULT 0,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    updated_by BIGINT,
    is_active SMALLINT NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_chat_trip ON chat_messages(trip_id, created_at);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50),
    title VARCHAR(255),
    body TEXT,
    data TEXT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted INT NOT NULL DEFAULT 0,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    updated_by BIGINT,
    is_active SMALLINT NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_notif_user ON notifications(user_id, created_at);

CREATE TABLE IF NOT EXISTS app_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    category VARCHAR(30) NOT NULL,
    is_secret BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    is_deleted INT NOT NULL DEFAULT 0,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    updated_by BIGINT,
    is_active SMALLINT NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_config_category ON app_config(category);
