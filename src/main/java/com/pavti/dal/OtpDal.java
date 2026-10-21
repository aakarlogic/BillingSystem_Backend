package com.pavti.dal;

import com.pavti.dataobject.auth.OtpRow;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** One-time codes (user_otps). */
@Repository
public class OtpDal {

    private final DataClass db;

    public OtpDal(DataClass db) {
        this.db = db;
    }

    public void create(String mobileNo, String purpose, String otpHash, int minutes) {
        db.getLong("sp_otp_create", mobileNo, purpose, otpHash, minutes);
    }

    public Optional<OtpRow> getLatest(String mobileNo, String purpose) {
        return db.getOne("sp_otp_get_latest", OtpRow.class, mobileNo, purpose);
    }

    public void addAttempt(long otpId) {
        db.execute("sp_otp_add_attempt", otpId);
    }

    public void markUsed(long otpId) {
        db.execute("sp_otp_mark_used", otpId);
    }
}
