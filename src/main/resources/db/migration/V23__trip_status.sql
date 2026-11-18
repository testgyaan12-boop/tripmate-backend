-- TripMate V23: manual trip status override.
-- AUTO = derive from dates (Upcoming / Ongoing / Completed / Planning).
-- Explicit values (PLANNING, UPCOMING, ONGOING, COMPLETED, FINALIZED) win.
-- Existing PLANNING rows become AUTO so behaviour is unchanged.

ALTER TABLE trips ALTER COLUMN status SET DEFAULT 'AUTO';
UPDATE trips SET status = 'AUTO' WHERE status = 'PLANNING';
