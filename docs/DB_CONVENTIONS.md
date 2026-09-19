# TripMate DB Conventions

Every table extends `BaseEntity` (`com.tripmate.common.entity.BaseEntity`):

| Column | Type (PG) | Rule |
|---|---|---|
| `id` | BIGSERIAL PK | All PKs BIGINT to match `created_by` |
| `is_deleted` | INT DEFAULT 0 | 0=alive, 1=soft-deleted |
| `remarks` | VARCHAR(150) | Optional operator note |
| `created_at` | TIMESTAMPTZ | Auto via JPA auditing |
| `created_by` | BIGINT | JWT user id via `AuditorAware`, null on seeds |
| `updated_at` | TIMESTAMPTZ | Auto |
| `updated_by` | BIGINT | JWT user id |
| `is_active` | SMALLINT DEFAULT 1 | Spec TINYINT -> PG SMALLINT |

## Soft-delete rule
- Never physical `DELETE`. Call `entity.softDelete()` + save (sets `is_deleted=1, is_active=0`).
- Reads auto-filtered by `@SQLRestriction("is_deleted = 0 AND is_active = 1")`.
- IMPORTANT: the annotation is declared on `BaseEntity` AND repeated on every
  entity class — Hibernate ignores it when present only on a mapped superclass
  (verified: without per-entity repetition, soft-deleted rows leak into queries).
- Missing/soft-deleted row -> API `404`.
- Index each table on `(is_deleted, is_active)` or domain FK.

## JWT-only auth
- `users.password_hash` nullable (Google users have null until they set password).
- No Firebase Auth. Google `idToken` verified server-side via Google certs, then own JWT minted.

## Config
- All credentials in `app_config` except `JWT_SECRET`, `DB_*`, `CONFIG_MASTER_KEY` (env).
- `is_secret=true` masked in admin list, slated for AES-GCM with master key.
- Public clients use `GET /api/config/public` whitelist only.
