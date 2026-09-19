-- V13: per-expense settled flag (Settled / Pending status)

ALTER TABLE expenses ADD COLUMN is_settled SMALLINT NOT NULL DEFAULT 0;

-- personal (non-split) expenses are settled by definition
UPDATE expenses SET is_settled = 1 WHERE split_type = 'PERSONAL';
