package com.pavti.dataobject.auth;

import jakarta.validation.constraints.NotBlank;

/** loginId = mobile number (or email). */
public record SignInRequest(
        @NotBlank(message = "Enter your mobile number.") String loginId,
        @NotBlank(message = "Enter your password.") String password) {}
