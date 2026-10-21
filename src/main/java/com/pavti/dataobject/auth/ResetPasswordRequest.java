package com.pavti.dataobject.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Forgot password: mobile + OTP + new password. */
public record ResetPasswordRequest(
        @NotBlank String mobileNo,
        @NotBlank(message = "Enter the OTP.") String otp,
        @NotBlank @Size(min = 6, max = 100, message = "Password must be at least 6 characters.") String newPassword) {}
