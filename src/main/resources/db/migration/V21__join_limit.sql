-- TripMate V21: cap on trips a free user can JOIN (created trips uncapped here)

INSERT INTO app_config (config_key, config_value, category, is_secret, description, is_deleted, is_active, remarks)
VALUES ('free.trip.join.limit', '2', 'TRIP', false, 'Max trips a free user can join (MEMBER role)', 0, 1, 'seed-v21')
ON CONFLICT (config_key) DO NOTHING;
