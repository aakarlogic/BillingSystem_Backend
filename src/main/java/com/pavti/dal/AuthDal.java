package com.pavti.dal;

import com.pavti.dataobject.auth.LoginUserRow;
import com.pavti.dataobject.auth.SessionUserRow;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Login, sign-up and profile procedures (Health 360: LoginDAL). */
@Repository
public class AuthDal {

    private final DataClass db;

    public AuthDal(DataClass db) {
        this.db = db;
    }

    public Optional<LoginUserRow> getUserForLogin(String loginId) {
        return db.getOne("sp_auth_get_user_for_login", LoginUserRow.class, loginId);
    }

    public void recordLogin(Long userId, String loginId, boolean success, String failureReason, String ip, String device) {
        db.execute("sp_auth_record_login", userId, loginId, success ? 1 : 0, failureReason, ip, device);
    }

    public Optional<SessionUserRow> getSessionUser(long userId) {
        return db.getOne("sp_auth_get_session_user", SessionUserRow.class, userId);
    }

    public List<String> getUserPermissions(long userId) {
        return db.getStrings("sp_auth_get_user_permissions", userId);
    }

    public Optional<String> getPasswordHash(long userId) {
        return db.getString("sp_auth_get_password_hash", userId);
    }

    public void changePassword(long userId, String passwordHash) {
        db.execute("sp_auth_change_password", userId, passwordHash);
    }

    public void updateProfile(long userId, String fullName, String language) {
        db.execute("sp_auth_update_profile", userId, fullName, language);
    }

    public boolean mobileExists(String mobileNo, Long exceptUserId) {
        return db.getLong("sp_auth_mobile_exists", mobileNo, exceptUserId) > 0;
    }

    public record RegisterResult(long businessId, long userId) {}

    public RegisterResult registerBusiness(String businessName, String businessType, String ownerName, String mobileNo,
                                           String email, String passwordHash, String billPrefix, String planCode,
                                           boolean mobileVerified, Long createdBy) {
        return db.getOne("sp_auth_register_business", RegisterResult.class, businessName, businessType, ownerName, mobileNo,
                        email, passwordHash, billPrefix, planCode, mobileVerified ? 1 : 0, createdBy)
                .orElseThrow();
    }
}
