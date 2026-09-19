# Table: app_config

Extends BaseEntity (is_deleted, remarks, created_at, created_by, updated_at, updated_by, is_active).

- Alive filter: is_deleted=0 AND is_active=1 (automatic via SQLRestriction).
- Writes set created_by/updated_by from JWT via AuditorAware.
- Deletes are soft (is_deleted=1, is_active=0).

See ../DB_CONVENTIONS.md and V1__init.sql for DDL.
