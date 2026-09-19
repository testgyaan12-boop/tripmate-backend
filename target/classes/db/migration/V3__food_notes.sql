-- TripMate V3: food notes on itinerary legs (Stay already exists as stay_notes)
ALTER TABLE itinerary_items ADD COLUMN IF NOT EXISTS food_notes TEXT;
