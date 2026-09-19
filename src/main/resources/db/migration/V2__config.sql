-- TripMate V2: seed app_config (local values; secrets filled via admin API, never code)

INSERT INTO app_config (config_key, config_value, category, is_secret, description, is_deleted, is_active, remarks)
VALUES
('free.trip.limit', '2', 'APP', false, 'Number of free trips allowed', 0, 1, 'seed'),
('routing.provider', 'openrouteservice', 'ROUTING', false, 'Routing provider - ORS only, no OSRM', 0, 1, 'seed'),
('map.provider', 'openstreetmap', 'MAP', false, 'Map provider', 0, 1, 'seed'),
('storage.max-file-size', '20971520', 'STORAGE', false, 'Maximum upload size in bytes (20MB)', 0, 1, 'seed'),
('app.name', 'TripMate', 'APP', false, 'App display name', 0, 1, 'seed'),
('app.env', 'dev', 'APP', false, 'dev|prod', 0, 1, 'seed'),
('app.invite.base-url', 'http://localhost:5000/#/join?code=', 'APP', false, 'Invite deep-link base', 0, 1, 'seed'),
('ors.base.url', 'https://api.openrouteservice.org', 'ROUTING', false, 'ORS base URL', 0, 1, 'seed'),
('ors.api.key', 'REPLACE_VIA_ADMIN', 'ROUTING', true, 'ORS key - fill via PUT /api/admin/config', 0, 1, 'seed-todo'),
('ors.timeout.ms', '8000', 'ROUTING', false, 'ORS timeout ms', 0, 1, 'seed'),
('map.tile.url', 'https://tile.openstreetmap.org/{z}/{x}/{y}.png', 'MAP', false, 'OSM tile template', 0, 1, 'seed'),
('map.attribution', '© OpenStreetMap contributors', 'MAP', false, 'Map attribution', 0, 1, 'seed'),
('map.default.lat', '19.0760', 'MAP', false, 'Default latitude (Mumbai)', 0, 1, 'seed'),
('map.default.lng', '72.8777', 'MAP', false, 'Default longitude', 0, 1, 'seed'),
('jwt.access.ttl.min', '15', 'APP', false, 'Access token TTL minutes', 0, 1, 'seed'),
('jwt.refresh.ttl.days', '7', 'APP', false, 'Refresh token TTL days', 0, 1, 'seed'),
('google.client.id.web', 'REPLACE_VIA_ADMIN', 'AUTH_GOOGLE', false, 'Google web client ID', 0, 1, 'seed-todo'),
('google.client.id.android', 'REPLACE_VIA_ADMIN', 'AUTH_GOOGLE', false, 'Google Android client ID', 0, 1, 'seed-todo'),
('trip.max.members', '20', 'TRIP', false, 'Max members per trip', 0, 1, 'seed'),
('trip.max.places', '50', 'TRIP', false, 'Max places per trip', 0, 1, 'seed'),
('trip.invite.expiry.days', '7', 'TRIP', false, 'Invite validity days', 0, 1, 'seed'),
('storage.provider', 'local', 'STORAGE', false, 'local|minio|s3', 0, 1, 'seed'),
('storage.endpoint', 'http://localhost:9000', 'STORAGE', false, 'S3/MinIO endpoint', 0, 1, 'seed'),
('storage.bucket', 'tripmate', 'STORAGE', false, 'Bucket name', 0, 1, 'seed'),
('storage.access.key', 'minioadmin', 'STORAGE', true, 'Local only - change in prod', 0, 1, 'seed-local'),
('storage.secret.key', 'minioadmin', 'STORAGE', true, 'Local only - change in prod', 0, 1, 'seed-local'),
('fcm.enabled', 'false', 'FCM', false, 'Push enabled flag', 0, 1, 'seed'),
('fcm.service-account.json', '{}', 'FCM', true, 'Service-account JSON for push', 0, 1, 'seed-todo')
ON CONFLICT (config_key) DO NOTHING;
