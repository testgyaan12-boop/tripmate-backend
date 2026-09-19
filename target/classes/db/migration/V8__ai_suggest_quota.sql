-- TripMate V8: AI suggestion quota (batches per trip)
INSERT INTO app_config (config_key, config_value, category, is_secret, description, is_deleted, is_active, remarks)
VALUES
('ai.free.suggestions.per.trip', '5', 'AI', false, 'Free AI suggestion batches per trip', 0, 1, 'seed')
ON CONFLICT (config_key) DO NOTHING;
