CREATE TABLE trip_invite (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    inviter_user_id BIGINT NOT NULL,
    invitee_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    is_deleted INTEGER NOT NULL DEFAULT 0,
    remarks VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    is_active SMALLINT NOT NULL DEFAULT 1,
    UNIQUE(trip_id, invitee_user_id, is_deleted)
);

CREATE INDEX idx_invite_invitee ON trip_invite(invitee_user_id, status) WHERE is_deleted = 0;
CREATE INDEX idx_invite_trip ON trip_invite(trip_id) WHERE is_deleted = 0;
