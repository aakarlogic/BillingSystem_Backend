package com.pavti.bal;

import static com.pavti.common.TextHelper.clean;

import com.pavti.common.ApiException;
import com.pavti.common.JwtTokenHelper;
import com.pavti.common.PasswordHelper;
import com.pavti.common.TextHelper;
import com.pavti.config.AppProperties;
import com.pavti.dal.AuthDal;
import com.pavti.dal.OtpDal;
import com.pavti.dal.SessionDal;
import com.pavti.dataobject.auth.ChangePasswordRequest;
import com.pavti.dataobject.auth.LoginUserRow;
import com.pavti.dataobject.auth.OtpRow;
import com.pavti.dataobject.auth.RegisterRequest;
import com.pavti.dataobject.auth.ResetPasswordRequest;
import com.pavti.dataobject.auth.SendOtpData;
import com.pavti.dataobject.auth.SendOtpRequest;
import com.pavti.dataobject.auth.SessionRow;
import com.pavti.dataobject.auth.SessionUser;
import com.pavti.dataobject.auth.SessionUserRow;
import com.pavti.dataobject.auth.SignInData;
import com.pavti.dataobject.auth.SignInRequest;
import com.pavti.dataobject.auth.UpdateProfileRequest;
import com.pavti.filter.CurrentUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication (Health 360: LoginBAL + AuthTokenBAL).
 * Passwords and OTPs are checked here with BCrypt; the database only stores hashes.
 */
@Service
public class AuthBal {

    private static final Logger log = LoggerFactory.getLogger(AuthBal.class);

    private final AuthDal authDal;
    private final SessionDal sessionDal;
    private final OtpDal otpDal;
    private final PasswordHelper passwords;
    private final JwtTokenHelper jwt;
    private final AuditBal audit;
    private final AppProperties props;

    public AuthBal(AuthDal authDal, SessionDal sessionDal, OtpDal otpDal, PasswordHelper passwords, JwtTokenHelper jwt,
                   AuditBal audit, AppProperties props) {
        this.authDal = authDal;
        this.sessionDal = sessionDal;
        this.otpDal = otpDal;
        this.passwords = passwords;
        this.jwt = jwt;
        this.audit = audit;
        this.props = props;
    }

    // ------------------------------------------------------------ login

    public SignInData login(SignInRequest req, String ip, String device) {
        String loginId = clean(req.loginId()).contains("@") ? clean(req.loginId()).toLowerCase() : TextHelper.mobile(req.loginId());
        LoginUserRow u = authDal.getUserForLogin(loginId).orElse(null);

        if (u == null) {
            authDal.recordLogin(null, loginId, false, "UNKNOWN_LOGIN", ip, device);
            throw ApiException.unauthorized("Wrong mobile number or password.");
        }
        if (u.lockedUntil() != null && u.lockedUntil().isAfter(Instant.now())) {
            authDal.recordLogin(u.userId(), loginId, false, "LOCKED", ip, device);
            throw ApiException.forbidden("Too many wrong tries. Try again after 15 minutes.");
        }
        if (!passwords.matches(req.password(), u.passwordHash())) {
            authDal.recordLogin(u.userId(), loginId, false, "WRONG_PASSWORD", ip, device);
            throw ApiException.unauthorized("Wrong mobile number or password.");
        }
        if (!u.isActive()) {
            authDal.recordLogin(u.userId(), loginId, false, "INACTIVE", ip, device);
            throw ApiException.forbidden("Your login is turned off. Ask your owner.");
        }
        if (u.businessId() != null && !"ACTIVE".equals(u.businessStatus())) {
            authDal.recordLogin(u.userId(), loginId, false, "BUSINESS_SUSPENDED", ip, device);
            throw ApiException.forbidden("This business is paused. Contact support.");
        }

        authDal.recordLogin(u.userId(), loginId, true, null, ip, device);
        audit.log(u.businessId(), u.userId(), "LOGIN", "users", u.userId(), u.mobileNo(), null);
        return startSession(u.userId(), u.businessId(), u.roleCode(), u.mustChangePassword(), ip, device);
    }

    /** New session row + access token + refresh token. */
    private SignInData startSession(long userId, Long businessId, String roleCode, boolean mustChangePassword, String ip, String device) {
        String refreshToken = passwords.newToken();
        Instant expires = Instant.now().plus(props.jwt().refreshTokenDays(), ChronoUnit.DAYS);
        long sessionId = sessionDal.create(userId, businessId, passwords.sha256(refreshToken), device, ip, expires);
        String accessToken = jwt.createAccessToken(userId, businessId, roleCode, sessionId);
        return new SignInData(accessToken, refreshToken, sessionUser(userId), mustChangePassword);
    }

    /** A fresh access token for a still-open session (the refresh token stays the same). */
    public SignInData refresh(String refreshToken) {
        SessionRow s = sessionDal.getByTokenHash(passwords.sha256(clean(refreshToken)))
                .orElseThrow(() -> ApiException.unauthorized("Your session has ended. Please sign in again."));
        SessionUserRow u = authDal.getSessionUser(s.userId())
                .filter(SessionUserRow::isActive)
                .orElseThrow(() -> ApiException.unauthorized("Please sign in again."));
        sessionDal.touch(s.sessionId());
        String accessToken = jwt.createAccessToken(u.userId(), u.businessId(), u.roleCode(), s.sessionId());
        return new SignInData(accessToken, clean(refreshToken), sessionUser(u.userId()), false);
    }

    public void logout(CurrentUser user) {
        sessionDal.close(user.sessionId(), user.userId());
        audit.log(user, "LOGOUT", "users", user.userId(), null, null);
    }

    public SessionUser sessionUser(long userId) {
        SessionUserRow u = authDal.getSessionUser(userId).orElseThrow(() -> ApiException.unauthorized("Please sign in again."));
        List<String> permissions = "STAFF".equals(u.roleCode()) ? authDal.getUserPermissions(userId) : RightsBal.ALL_PERMISSIONS;
        return new SessionUser(u.userId(), u.businessId(), u.businessName(), u.roleCode(), u.fullName(), u.mobileNo(),
                u.email(), permissions, u.preferredLanguage(), u.currencyCode());
    }

    // ------------------------------------------------------------ OTP + sign-up

    public SendOtpData sendOtp(SendOtpRequest req) {
        String mobile = TextHelper.mobile(req.mobileNo());
        if (!TextHelper.isMobile(mobile)) throw ApiException.badRequest("Enter a 10-digit mobile number.");
        boolean exists = authDal.mobileExists(mobile, null);
        if ("REGISTER".equals(req.purpose()) && exists) throw ApiException.conflict("This mobile number already has an account. Please sign in.");
        if ("RESET_PASSWORD".equals(req.purpose()) && !exists) throw ApiException.notFound("No account with this mobile number.");

        String otp = passwords.newOtp();
        otpDal.create(mobile, req.purpose(), passwords.hash(otp), props.otp().minutesValid());
        if (props.otp().devMode()) {
            log.info("DEV OTP for {} ({}): {}", mobile, req.purpose(), otp);
            return new SendOtpData(true, props.otp().minutesValid(), otp);
        }
        // TODO production: send the code by SMS (e.g. MSG91 / Twilio) here
        return new SendOtpData(true, props.otp().minutesValid(), null);
    }

    /** Checks the newest code for this mobile + purpose; 3 wrong tries and it is used up. */
    private void verifyOtp(String mobile, String purpose, String otp) {
        OtpRow row = otpDal.getLatest(mobile, purpose).orElseThrow(() -> ApiException.badRequest("Ask for a new OTP."));
        if (row.usedDate() != null || row.expiresAt().isBefore(Instant.now())) throw ApiException.badRequest("This OTP has expired. Ask for a new one.");
        if (row.attempts() >= props.otp().maxAttempts()) throw ApiException.badRequest("Too many wrong OTPs. Ask for a new one.");
        if (!passwords.matches(clean(otp), row.otpHash())) {
            otpDal.addAttempt(row.otpId());
            throw ApiException.badRequest("Wrong OTP.");
        }
        otpDal.markUsed(row.otpId());
    }

    @Transactional
    public SignInData register(RegisterRequest req, String ip, String device) {
        String mobile = TextHelper.mobile(req.mobileNo());
        if (!TextHelper.isMobile(mobile)) throw ApiException.badRequest("Enter a 10-digit mobile number.");
        if (authDal.mobileExists(mobile, null)) throw ApiException.conflict("This mobile number already has an account. Please sign in.");
        verifyOtp(mobile, "REGISTER", req.otp());

        String name = clean(req.businessName());
        String prefix = name.replaceAll("[^A-Za-z]", "").toUpperCase();
        prefix = (prefix.length() >= 3 ? prefix.substring(0, 3) : "BIL") + "-";
        AuthDal.RegisterResult r = authDal.registerBusiness(name, req.businessType() == null ? "RESTAURANT" : req.businessType(),
                clean(req.ownerName()), mobile, clean(req.email()).toLowerCase(), passwords.hash(req.password()), prefix, "FREE",
                true, null);
        audit.log(r.businessId(), r.userId(), "BUSINESS_REGISTER", "businesses", r.businessId(), name, null);
        return startSession(r.userId(), r.businessId(), "OWNER", false, ip, device);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        String mobile = TextHelper.mobile(req.mobileNo());
        LoginUserRow u = authDal.getUserForLogin(mobile).orElseThrow(() -> ApiException.notFound("No account with this mobile number."));
        verifyOtp(mobile, "RESET_PASSWORD", req.otp());
        authDal.changePassword(u.userId(), passwords.hash(req.newPassword()));
        sessionDal.closeAll(u.userId(), u.userId());
        audit.log(u.businessId(), u.userId(), "PASSWORD_RESET", "users", u.userId(), null, null);
    }

    // ------------------------------------------------------------ profile

    public void changePassword(CurrentUser user, ChangePasswordRequest req) {
        String hash = authDal.getPasswordHash(user.userId()).orElseThrow(() -> ApiException.unauthorized("Please sign in again."));
        if (!passwords.matches(req.currentPassword(), hash)) throw ApiException.badRequest("Current password is wrong.");
        if (req.currentPassword().equals(req.newPassword())) throw ApiException.badRequest("New password must be different.");
        authDal.changePassword(user.userId(), passwords.hash(req.newPassword()));
        audit.log(user, "PASSWORD_CHANGE", "users", user.userId(), null, null);
    }

    public SessionUser updateProfile(CurrentUser user, UpdateProfileRequest req) {
        authDal.updateProfile(user.userId(), clean(req.fullName()), req.preferredLanguage() == null ? "mr" : req.preferredLanguage());
        return sessionUser(user.userId());
    }
}
