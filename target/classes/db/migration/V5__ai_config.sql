-- TripMate V5: AI itinerary config (keys set via admin API, never code)
INSERT INTO app_config (config_key, config_value, category, is_secret, description, is_deleted, is_active, remarks)
VALUES
('ai.provider', 'gemini', 'AI', false, 'Primary AI provider', 0, 1, 'seed'),
('ai.fallback.provider', 'groq', 'AI', false, 'Fallback AI provider', 0, 1, 'seed'),
('ai.gemini.api.key', 'REPLACE_VIA_ADMIN', 'AI', true, 'Gemini API key - set via admin', 0, 1, 'seed-todo'),
('ai.gemini.model', 'gemini-2.0-flash', 'AI', false, 'Gemini model', 0, 1, 'seed'),
('ai.groq.api.key', 'REPLACE_VIA_ADMIN', 'AI', true, 'Groq API key - set via admin', 0, 1, 'seed-todo'),
('ai.groq.model', 'llama-3.3-70b-versatile', 'AI', false, 'Groq model', 0, 1, 'seed'),
('ai.free.generations.per.trip', '2', 'AI', false, 'Free AI proposals per trip', 0, 1, 'seed'),
('ai.timeout.ms', '60000', 'AI', false, 'AI request timeout ms', 0, 1, 'seed')
ON CONFLICT (config_key) DO NOTHING;
