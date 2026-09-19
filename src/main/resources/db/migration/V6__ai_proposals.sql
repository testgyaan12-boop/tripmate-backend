-- TripMate V6: AI itinerary proposals (draft -> confirm flow, BaseEntity audit cols)
CREATE TABLE IF NOT EXISTS ai_itinerary_proposals (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    provider VARCHAR(20),
    model VARCHAR(60),
    questions TEXT,
    payload TEXT,
    status VARCHAR(12) NOT NULL DEFAULT 'PROPOSED',
    is_deleted INT NOT NULL DEFAULT 0,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    updated_by BIGINT,
    is_active SMALLINT NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_aiprop_trip ON ai_itinerary_proposals(trip_id);
