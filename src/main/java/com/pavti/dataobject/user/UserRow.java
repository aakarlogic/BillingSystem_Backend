package com.pavti.dataobject.user;

/** Row of sp_user_get_all; permissionCodes is a comma list. */
public record UserRow(
        long userId,
        Long businessId,
        String businessName,
        String roleCode,
        String fullName,
        String mobileNo,
        String email,
        String preferredLanguage,
        boolean isActive,
        String lastLoginDate,
        String permissionCodes) {}
