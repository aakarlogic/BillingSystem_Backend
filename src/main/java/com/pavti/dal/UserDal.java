package com.pavti.dal;

import com.pavti.dataobject.user.UserBrief;
import com.pavti.dataobject.user.UserRow;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Team members and platform users. */
@Repository
public class UserDal {

    private final DataClass db;

    public UserDal(DataClass db) {
        this.db = db;
    }

    /** businessId null = everyone (platform admin). */
    public List<UserRow> getAll(Long businessId) {
        return db.getList("sp_user_get_all", UserRow.class, businessId);
    }

    public Optional<UserBrief> getById(long userId) {
        return db.getOne("sp_user_get_by_id", UserBrief.class, userId);
    }

    public long countActive(long businessId) {
        return db.getLong("sp_user_count_active", businessId);
    }

    /** passwordHash null = keep the old password. Returns the user id. */
    public long save(Long userId, Long businessId, String roleCode, String fullName, String mobileNo, String email,
                     String passwordHash, long by) {
        return db.getLong("sp_user_save", userId, businessId, roleCode, fullName, mobileNo, email, passwordHash, by);
    }

    public void setPermissions(long userId, long businessId, List<String> codes, long by) {
        db.execute("sp_user_set_permissions", userId, businessId, String.join(",", codes), by);
    }

    public void setStatus(long userId, boolean isActive, long by) {
        db.execute("sp_user_set_status", userId, isActive ? 1 : 0, by);
    }
}
