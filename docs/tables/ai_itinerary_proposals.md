# Table: ai_itinerary_proposals

Extends BaseEntity (is_deleted, remarks, created_at, created_by, updated_at, updated_by, is_active).

- Draft -> confirm flow for AI itineraries. Drafts never touch `itinerary_items`.
- Alive filter: is_deleted=0 AND is_active=1 (automatic via SQLRestriction).
- `status`: PROPOSED (counts toward per-trip quota) or CONFIRMED.
- Quota rule: `used = count(status IN PROPOSED,CONFIRMED)` per trip, limit from `app_config ai.free.generations.per.trip` (default 2). Failed attempts are free.
- Confirm writes edited days into `itinerary_items` (old items soft-deleted) and matches titles to places for images.
- Confirm auto-creates missing stops as places (geocoded, `remarks="ai-generated"`, cap from `ai.confirm.max.new.places`), persists `stops_json`, returns `{saved, placesCreated, placesLinked}`.

See ../DB_CONVENTIONS.md and V6__ai_proposals.sql for DDL.
