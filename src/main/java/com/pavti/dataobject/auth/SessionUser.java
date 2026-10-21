package com.pavti.dataobject.auth;

import java.util.List;

/** The signed-in person as the React app keeps it (types/auth.ts SessionUser). */
public record SessionUser(
        long userId,
        Long businessId,
        String businessName,
        String roleCode,
        String fullName,
        String mobileNo,
        String email,
        List<String> permissions,
        String preferredLanguage,
        String currencyCode) {}
