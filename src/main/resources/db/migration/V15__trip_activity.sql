CREATE TABLE trip_activity (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    user_id BIGINT,
    action VARCHAR(40) NOT NULL,
    detail TEXT,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    is_active SMALLINT NOT NULL DEFAULT 1
);

CREATE INDEX idx_activity_trip ON trip_activity(trip_id) WHERE is_deleted = 0;
CREATE INDEX idx_activity_user ON trip_activity(user_id) WHERE is_deleted = 0;
