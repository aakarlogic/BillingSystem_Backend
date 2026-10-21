package com.pavti.dataobject.user;

import java.util.List;

/** One person in the team list (React app: types/user.ts User). */
public record User(
        long userId,
        Long businessId,
        String businessName,
        String roleCode,
        String fullName,
        String mobileNo,
        String email,
        String preferredLanguage,
        List<String> permissions,
        boolean isActive,
        String lastLoginDate) {}
