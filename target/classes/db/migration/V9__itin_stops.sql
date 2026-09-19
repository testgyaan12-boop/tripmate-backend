-- TripMate V9: persist AI/confirmed day stops on itinerary legs
ALTER TABLE itinerary_items ADD COLUMN IF NOT EXISTS stops_json TEXT;
