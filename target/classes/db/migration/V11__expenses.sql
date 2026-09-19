-- V11: expenses table with split/settle support

CREATE TABLE expenses (
    id              BIGSERIAL PRIMARY KEY,
    trip_id         BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    day_no          INT NOT NULL DEFAULT 1,
    category        VARCHAR(30) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    amount DOUBLE PRECISION NOT NULL DEFAULT 0,
    currency        VARCHAR(3) NOT NULL DEFAULT 'INR',
    distance_km     DOUBLE PRECISION,
    notes           TEXT,
    added_by        BIGINT NOT NULL,
    paid_by         BIGINT NOT NULL,
    split_type      VARCHAR(20) NOT NULL DEFAULT 'EQUAL',
    split_members   JSONB,
    is_deleted      INT NOT NULL DEFAULT 0,
    remarks         VARCHAR(150),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_by      BIGINT,
    is_active       SMALLINT NOT NULL DEFAULT 1
);

CREATE INDEX idx_expenses_trip ON expenses(trip_id);
