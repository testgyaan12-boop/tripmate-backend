-- TripMate V20: subscription & payment (DB-driven plans, Razorpay test keys)

CREATE TABLE plan_master (
    id BIGSERIAL PRIMARY KEY,
    plan_code VARCHAR(20) NOT NULL UNIQUE,
    plan_name VARCHAR(50) NOT NULL,
    monthly_price_paise INTEGER NOT NULL DEFAULT 0,
    yearly_price_paise INTEGER NOT NULL DEFAULT 0,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    is_active SMALLINT NOT NULL DEFAULT 1,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT
);

CREATE TABLE plan_feature (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES plan_master(id),
    feature_code VARCHAR(40) NOT NULL,
    limit_value VARCHAR(40) NOT NULL DEFAULT '',
    is_deleted INTEGER NOT NULL DEFAULT 0,
    is_active SMALLINT NOT NULL DEFAULT 1,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    UNIQUE(plan_id, feature_code)
);
CREATE INDEX idx_plan_feature_plan ON plan_feature(plan_id, is_deleted, is_active);

CREATE TABLE user_subscription (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    plan_id BIGINT NOT NULL REFERENCES plan_master(id),
    payment_gateway VARCHAR(20) NOT NULL DEFAULT 'RAZORPAY',
    gateway_subscription_id VARCHAR(100),
    billing_cycle VARCHAR(10) NOT NULL DEFAULT 'MONTHLY',
    start_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    end_date TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_deleted INTEGER NOT NULL DEFAULT 0,
    is_active SMALLINT NOT NULL DEFAULT 1,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    UNIQUE(gateway_subscription_id)
);
CREATE INDEX idx_user_sub_user ON user_subscription(user_id, is_deleted, is_active);
CREATE INDEX idx_user_sub_status ON user_subscription(user_id, status) WHERE is_deleted = 0;

CREATE TABLE payment_transaction (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    subscription_id BIGINT REFERENCES user_subscription(id),
    payment_gateway VARCHAR(20) NOT NULL DEFAULT 'RAZORPAY',
    gateway_order_id VARCHAR(100),
    gateway_payment_id VARCHAR(100),
    amount_paise INTEGER NOT NULL DEFAULT 0,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    plan_code VARCHAR(20),
    billing_cycle VARCHAR(10),
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    is_deleted INTEGER NOT NULL DEFAULT 0,
    is_active SMALLINT NOT NULL DEFAULT 1,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    UNIQUE(gateway_payment_id)
);
CREATE INDEX idx_pay_txn_user ON payment_transaction(user_id, is_deleted, is_active);

-- Per-file bytes for storage-quota accounting (SUM per user).
ALTER TABLE trip_gallery ADD COLUMN IF NOT EXISTS file_size_bytes BIGINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_gallery_user_bytes ON trip_gallery(user_id, is_deleted, is_active);

-- Plans: FREE 3 trips / 10 members / 500MB / no AI; PRO 99-799 / unlimited / 10GB / AI; FAMILY 199 / 50GB.
INSERT INTO plan_master (plan_code, plan_name, monthly_price_paise, yearly_price_paise, currency, is_active, sort_order, is_deleted, remarks)
VALUES
('FREE', 'TripMate Free', 0, 0, 'INR', 1, 0, 0, 'seed'),
('PRO', 'TripMate Pro', 9900, 79900, 'INR', 1, 1, 0, 'seed'),
('FAMILY', 'TripMate Family', 19900, 199900, 'INR', 1, 2, 0, 'seed')
ON CONFLICT (plan_code) DO NOTHING;

INSERT INTO plan_feature (plan_id, feature_code, limit_value, is_deleted, is_active, remarks)
SELECT p.id, f.feature_code, f.val, 0, 1, 'seed' FROM plan_master p
JOIN (VALUES
    ('FREE','TRIP_LIMIT','3'), ('FREE','MEMBERS_PER_TRIP','10'),
    ('FREE','STORAGE_MB','500'), ('FREE','AI_PLANNER','0'),
    ('FREE','OFFLINE_MAP','0'), ('FREE','EXPORT_PDF','0'), ('FREE','NO_ADS','0'),
    ('PRO','TRIP_LIMIT','999'), ('PRO','MEMBERS_PER_TRIP','999'),
    ('PRO','STORAGE_MB','10240'), ('PRO','AI_PLANNER','1'),
    ('PRO','OFFLINE_MAP','1'), ('PRO','EXPORT_PDF','1'), ('PRO','NO_ADS','1'),
    ('FAMILY','TRIP_LIMIT','999'), ('FAMILY','MEMBERS_PER_TRIP','999'),
    ('FAMILY','STORAGE_MB','51200'), ('FAMILY','AI_PLANNER','1'),
    ('FAMILY','OFFLINE_MAP','1'), ('FAMILY','EXPORT_PDF','1'), ('FAMILY','NO_ADS','1'),
    ('FAMILY','PRIORITY_SUPPORT','1')
) AS f(code, feature_code, val) ON f.code = p.plan_code
ON CONFLICT (plan_id, feature_code) DO NOTHING;

-- Keep legacy fallback consistent with FREE plan (3 trips).
UPDATE app_config SET config_value = '3', remarks = 'seed-v20'
WHERE config_key = 'free.trip.limit' AND config_value = '2';

-- Razorpay test credentials (test mode; switch values for live).
INSERT INTO app_config (config_key, config_value, category, is_secret, description, is_deleted, is_active, remarks)
VALUES
('RAZORPAY_KEY_ID', 'rzp_test_TZw7jz9vAaX4P9', 'BILLING', false, 'Razorpay key id (public) - test', 0, 1, 'seed-v20'),
('RAZORPAY_SECRET', 'bjQDhJ9I0YAe5fEdO37dpSqB', 'BILLING', true, 'Razorpay secret - test, never expose', 0, 1, 'seed-v20'),
('RAZORPAY_WEBHOOK_SECRET', 'REPLACE_VIA_ADMIN', 'BILLING', true, 'Webhook secret from Razorpay dashboard', 0, 1, 'seed-todo')
ON CONFLICT (config_key) DO NOTHING;
