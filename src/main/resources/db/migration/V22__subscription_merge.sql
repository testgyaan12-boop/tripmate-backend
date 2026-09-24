-- TripMate V22: merge plan_master + plan_feature into one subscription table.
-- Plans + description + limits live in details JSON; user_subscription links by id.

CREATE TABLE subscription (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    monthly_price_paise INTEGER NOT NULL DEFAULT 0,
    yearly_price_paise INTEGER NOT NULL DEFAULT 0,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    sort_order INTEGER NOT NULL DEFAULT 0,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    is_active SMALLINT NOT NULL DEFAULT 1,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT
);

-- Migrate existing plans: features aggregated into details.features, plus description.
INSERT INTO subscription (code, name, monthly_price_paise, yearly_price_paise,
    currency, sort_order, details, is_deleted, is_active, remarks, created_at, updated_at)
SELECT p.plan_code, p.plan_name, p.monthly_price_paise, p.yearly_price_paise,
    p.currency, p.sort_order,
    jsonb_build_object(
        'description', CASE p.plan_code
            WHEN 'FREE' THEN 'Starter plan for casual travellers'
            WHEN 'PRO' THEN 'Unlimited trips, AI planner and 10 GB gallery'
            WHEN 'FAMILY' THEN 'Everything in Pro plus 50 GB gallery and priority support'
            ELSE '' END,
        'features', COALESCE((
            SELECT jsonb_object_agg(f.feature_code, f.limit_value)
            FROM plan_feature f
            WHERE f.plan_id = p.id AND f.is_deleted = 0 AND f.is_active = 1
        ), '{}'::jsonb)),
    p.is_deleted, p.is_active, p.remarks, p.created_at, p.updated_at
FROM plan_master p
ON CONFLICT (code) DO NOTHING;

-- Re-link subscriptions: user_subscription.plan_id -> subscription.id
ALTER TABLE user_subscription ADD COLUMN IF NOT EXISTS subscription_id BIGINT REFERENCES subscription(id);

UPDATE user_subscription u SET subscription_id = s.id
FROM plan_master p JOIN subscription s ON s.code = p.plan_code
WHERE u.plan_id = p.id AND u.subscription_id IS NULL;

-- Orphans (plan deleted) fall back to FREE, then enforce NOT NULL.
UPDATE user_subscription SET subscription_id = (SELECT id FROM subscription WHERE code = 'FREE')
WHERE subscription_id IS NULL;
ALTER TABLE user_subscription ALTER COLUMN subscription_id SET NOT NULL;

-- Retire old tables (FK name follows PG convention; IF EXISTS guards).
ALTER TABLE user_subscription DROP CONSTRAINT IF EXISTS user_subscription_plan_id_fkey;
ALTER TABLE user_subscription DROP COLUMN IF EXISTS plan_id;
DROP TABLE IF EXISTS plan_feature;
DROP TABLE IF EXISTS plan_master;
