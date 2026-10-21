package com.pavti.dataobject.auth;

import java.time.Instant;

/** Row of sp_auth_get_user_for_login. */
public record LoginUserRow(
        long userId,
        Long businessId,
        String roleCode,
        String fullName,
        String mobileNo,
        String email,
        String passwordHash,
        String preferredLanguage,
        boolean isActive,
        int failedLoginCount,
        Instant lockedUntil,
        boolean mustChangePassword,
        String businessStatus) {}
