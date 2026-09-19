-- V14: exact expense module rebuild (spec tables).
-- Drops the earlier expenses/trip_budgets/expense_settlements design.

DROP TABLE IF EXISTS expense_settlements CASCADE;
DROP TABLE IF EXISTS trip_budgets CASCADE;
DROP TABLE IF EXISTS expenses CASCADE;
DROP TABLE IF EXISTS expense_split CASCADE;
DROP TABLE IF EXISTS trip_budget CASCADE;
DROP TABLE IF EXISTS trip_expense CASCADE;

CREATE TABLE trip_expense (
    id              BIGSERIAL PRIMARY KEY,
    trip_id         BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    amount          DOUBLE PRECISION NOT NULL DEFAULT 0,
    category        VARCHAR(30) NOT NULL,
    paid_by_user_id BIGINT NOT NULL,
    expense_date    DATE,
    day_no          INT NOT NULL DEFAULT 1,
    distance_km     DOUBLE PRECISION,
    notes           TEXT,
    added_by        BIGINT NOT NULL,
    split_type      VARCHAR(20) NOT NULL DEFAULT 'EQUAL',
    is_deleted      INT NOT NULL DEFAULT 0,
    remarks         VARCHAR(150),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_by      BIGINT,
    is_active       SMALLINT NOT NULL DEFAULT 1
);

CREATE INDEX idx_trip_expense_trip ON trip_expense(trip_id);

CREATE TABLE expense_split (
    id              BIGSERIAL PRIMARY KEY,
    expense_id      BIGINT NOT NULL REFERENCES trip_expense(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL,
    amount          DOUBLE PRECISION NOT NULL DEFAULT 0,
    status          VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    settled_via     VARCHAR(20),
    is_deleted      INT NOT NULL DEFAULT 0,
    remarks         VARCHAR(150),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_by      BIGINT,
    is_active       SMALLINT NOT NULL DEFAULT 1
);

CREATE INDEX idx_expense_split_expense ON expense_split(expense_id);

CREATE TABLE trip_budget (
    id              BIGSERIAL PRIMARY KEY,
    trip_id         BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    category        VARCHAR(30) NOT NULL,
    planned_amount  DOUBLE PRECISION NOT NULL DEFAULT 0,
    spent_amount    DOUBLE PRECISION NOT NULL DEFAULT 0,
    is_deleted      INT NOT NULL DEFAULT 0,
    remarks         VARCHAR(150),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_by      BIGINT,
    is_active       SMALLINT NOT NULL DEFAULT 1
);

CREATE UNIQUE INDEX uq_trip_budget_cat ON trip_budget(trip_id, category);
CREATE INDEX idx_trip_budget_trip ON trip_budget(trip_id);
