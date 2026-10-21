package com.pavti.dataobject.auth;

/** Row of sp_auth_get_session_user. */
public record SessionUserRow(
        long userId,
        Long businessId,
        String businessName,
        String roleCode,
        String fullName,
        String mobileNo,
        String email,
        String preferredLanguage,
        String currencyCode,
        String timeZone,
        boolean isActive,
        String businessStatus) {}
