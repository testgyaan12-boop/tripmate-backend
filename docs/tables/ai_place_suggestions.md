# Table: ai_place_suggestions

Extends BaseEntity (is_deleted, remarks, created_at, created_by, updated_at, updated_by, is_active).

- AI must-visit batches for the Places tab. Coordinates geocode-verified server-side.
- Alive filter: is_deleted=0 AND is_active=1 (automatic via SQLRestriction).
- `status`: SUGGESTED (counts toward quota) or ADDED (after batch-add).
- Quota rule: `used = count(status IN SUGGESTED,ADDED)` per trip, limit from `app_config ai.free.suggestions.per.trip` (default 5). Failed attempts are free.
- Adds go through `POST .../suggestions/{id}/add` (duplicates skipped by name).

See ../DB_CONVENTIONS.md and V7__ai_suggestions.sql for DDL.
