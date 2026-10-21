package com.pavti.controller;

import com.pavti.bal.AuthBal;
import com.pavti.bal.AuditBal;
import com.pavti.common.ApiResponse;
import com.pavti.dataobject.auth.ChangePasswordRequest;
import com.pavti.dataobject.auth.RefreshRequest;
import com.pavti.dataobject.auth.RegisterRequest;
import com.pavti.dataobject.auth.ResetPasswordRequest;
import com.pavti.dataobject.auth.SendOtpData;
import com.pavti.dataobject.auth.SendOtpRequest;
import com.pavti.dataobject.auth.SessionUser;
import com.pavti.dataobject.auth.SignInData;
import com.pavti.dataobject.auth.SignInRequest;
import com.pavti.dataobject.auth.UpdateProfileRequest;
import com.pavti.filter.CurrentUser;
import com.pavti.filter.ValidateEncryptedRequest;
import com.pavti.filter.ValidateToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sign-up, login, sessions, profile (Health 360: LoginController, route api/Auth). */
@RestController
@RequestMapping("/api/Auth")
@ValidateEncryptedRequest
public class AuthController extends BaseApiController {

    private final AuthBal authBal;

    public AuthController(AuthBal authBal) {
        this.authBal = authBal;
    }

    private static String device(HttpServletRequest r) {
        String ua = r.getHeader("User-Agent");
        return ua == null ? null : ua.substring(0, Math.min(150, ua.length()));
    }

    @PostMapping("/Login")
    public ApiResponse<SignInData> login(@Valid @RequestBody SignInRequest req, HttpServletRequest http) {
        return ok(authBal.login(req, AuditBal.clientIp(), device(http)), "Signed in");
    }

    @PostMapping("/SendOtp")
    public ApiResponse<SendOtpData> sendOtp(@Valid @RequestBody SendOtpRequest req) {
        return ok(authBal.sendOtp(req), "OTP sent");
    }

    @PostMapping("/Register")
    public ApiResponse<SignInData> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        return ok(authBal.register(req, AuditBal.clientIp(), device(http)), "Account created");
    }

    @PostMapping("/ResetPassword")
    public ApiResponse<Boolean> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authBal.resetPassword(req);
        return ok(true, "Password changed. Please sign in.");
    }

    @PostMapping("/Refresh")
    public ApiResponse<SignInData> refresh(@Valid @RequestBody RefreshRequest req) {
        return ok(authBal.refresh(req.refreshToken()));
    }

    @ValidateToken
    @PostMapping("/Logout")
    public ApiResponse<Boolean> logout(CurrentUser user) {
        authBal.logout(user);
        return ok(true, "Signed out");
    }

    @ValidateToken
    @PostMapping("/Me")
    public ApiResponse<SessionUser> me(CurrentUser user) {
        return ok(authBal.sessionUser(user.userId()));
    }

    @ValidateToken
    @PostMapping("/ChangePassword")
    public ApiResponse<Boolean> changePassword(@Valid @RequestBody ChangePasswordRequest req, CurrentUser user) {
        authBal.changePassword(user, req);
        return ok(true, "Password changed");
    }

    @ValidateToken
    @PostMapping("/UpdateProfile")
    public ApiResponse<SessionUser> updateProfile(@Valid @RequestBody UpdateProfileRequest req, CurrentUser user) {
        return ok(authBal.updateProfile(user, req), "Saved");
    }
}
