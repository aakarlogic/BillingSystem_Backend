package com.pavti.dataobject.auth;

/** What /Auth/Login returns (matches the React app's types/auth.ts SignInData). */
public record SignInData(String accessToken, String refreshToken, SessionUser user, boolean mustChangePassword) {}
