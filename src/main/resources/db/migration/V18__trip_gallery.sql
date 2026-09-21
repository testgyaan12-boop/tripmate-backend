CREATE TABLE trip_gallery (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    cloudinary_url TEXT NOT NULL,
    thumbnail_url TEXT,
    file_type VARCHAR(20) NOT NULL DEFAULT 'PHOTO',
    caption VARCHAR(500),
    location_name VARCHAR(200),
    album_name VARCHAR(100),
    like_count INTEGER NOT NULL DEFAULT 0,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    is_active SMALLINT NOT NULL DEFAULT 1,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT
);

CREATE INDEX idx_gallery_trip ON trip_gallery(trip_id, is_deleted, is_active);
CREATE INDEX idx_gallery_user ON trip_gallery(user_id);
CREATE INDEX idx_gallery_album ON trip_gallery(trip_id, album_name) WHERE album_name IS NOT NULL;

CREATE TABLE gallery_like (
    id BIGSERIAL PRIMARY KEY,
    gallery_id BIGINT NOT NULL REFERENCES trip_gallery(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    is_deleted INTEGER NOT NULL DEFAULT 0,
    is_active SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    UNIQUE(gallery_id, user_id)
);

CREATE TABLE gallery_comment (
    id BIGSERIAL PRIMARY KEY,
    gallery_id BIGINT NOT NULL REFERENCES trip_gallery(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    comment_text VARCHAR(500) NOT NULL,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    is_active SMALLINT NOT NULL DEFAULT 1,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT
);

CREATE INDEX idx_gcomment_gallery ON gallery_comment(gallery_id, is_deleted, is_active);

CREATE TABLE gallery_permission (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) UNIQUE,
    visibility VARCHAR(20) NOT NULL DEFAULT 'MEMBERS_ONLY',
    is_deleted INTEGER NOT NULL DEFAULT 0,
    is_active SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT
);
