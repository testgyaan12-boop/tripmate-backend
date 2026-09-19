-- TripMate V10: cap for AI place auto-creation per confirm
INSERT INTO app_config (config_key, config_value, category, is_secret, description, is_deleted, is_active, remarks)
VALUES
('ai.confirm.max.new.places', '8', 'AI', false, 'Max auto-created places per AI confirm', 0, 1, 'seed')
ON CONFLICT (config_key) DO NOTHING;
