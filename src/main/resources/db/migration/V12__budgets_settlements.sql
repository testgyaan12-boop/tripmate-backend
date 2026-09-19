-- V12: trip budgets + recorded settlements

CREATE TABLE trip_budgets (
    id              BIGSERIAL PRIMARY KEY,
    trip_id         BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    category        VARCHAR(30) NOT NULL,
    planned_amount  DOUBLE PRECISION NOT NULL DEFAULT 0,
    is_deleted      INT NOT NULL DEFAULT 0,
    remarks         VARCHAR(150),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_by      BIGINT,
    is_active       SMALLINT NOT NULL DEFAULT 1
);

CREATE UNIQUE INDEX uq_budget_trip_cat ON trip_budgets(trip_id, category);
CREATE INDEX idx_budgets_trip ON trip_budgets(trip_id);

CREATE TABLE expense_settlements (
    id              BIGSERIAL PRIMARY KEY,
    trip_id         BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    from_user_id    BIGINT NOT NULL,
    to_user_id      BIGINT NOT NULL,
    amount          DOUBLE PRECISION NOT NULL DEFAULT 0,
    method          VARCHAR(20) NOT NULL DEFAULT 'CASH',
    notes           TEXT,
    recorded_by     BIGINT NOT NULL,
    is_deleted      INT NOT NULL DEFAULT 0,
    remarks         VARCHAR(150),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_by      BIGINT,
    is_active       SMALLINT NOT NULL DEFAULT 1
);

CREATE INDEX idx_settlements_trip ON expense_settlements(trip_id);
