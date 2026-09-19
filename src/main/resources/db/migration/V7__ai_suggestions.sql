-- TripMate V7: AI place suggestion batches (BaseEntity audit cols)
CREATE TABLE IF NOT EXISTS ai_place_suggestions (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    provider VARCHAR(20),
    model VARCHAR(60),
    payload TEXT,
    status VARCHAR(12) NOT NULL DEFAULT 'SUGGESTED',
    is_deleted INT NOT NULL DEFAULT 0,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    updated_by BIGINT,
    is_active SMALLINT NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_aisug_trip ON ai_place_suggestions(trip_id);
