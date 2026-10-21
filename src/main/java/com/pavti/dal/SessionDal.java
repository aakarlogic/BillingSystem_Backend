package com.pavti.dal;

import com.pavti.dataobject.auth.SessionRow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Logged-in devices (user_sessions). */
@Repository
public class SessionDal {

    private final DataClass db;

    public SessionDal(DataClass db) {
        this.db = db;
    }

    public long create(long userId, Long businessId, String tokenHash, String device, String ip, Instant expiresAt) {
        return db.getLong("sp_session_create", userId, businessId, tokenHash, device, ip, Timestamp.from(expiresAt));
    }

    public boolean isActive(long sessionId, long userId) {
        return db.getLong("sp_session_is_active", sessionId, userId) > 0;
    }

    public Optional<SessionRow> getByTokenHash(String tokenHash) {
        return db.getOne("sp_session_get_by_token", SessionRow.class, tokenHash);
    }

    public void touch(long sessionId) {
        db.execute("sp_session_touch", sessionId);
    }

    public void close(long sessionId, long userId) {
        db.execute("sp_session_close", sessionId, userId);
    }

    public void closeAll(long userId, long by) {
        db.execute("sp_session_close_all", userId, by);
    }
}
