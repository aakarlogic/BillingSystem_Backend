package com.pavti.filter;

import com.pavti.common.ApiException;

/**
 * Who is calling, taken from the token (never from the request body).
 * Add a "CurrentUser user" parameter to a controller method to get it.
 */
public record CurrentUser(
        long userId,
        Long businessId,
        String roleCode,
        String fullName,
        long sessionId,
        String timeZone,
        String currencyCode) {

    public boolean isPlatformAdmin() {
        return "SUPER_ADMIN".equals(roleCode);
    }

    public boolean isOwner() {
        return "OWNER".equals(roleCode);
    }

    public boolean isStaff() {
        return "STAFF".equals(roleCode);
    }

    /** The caller's business; restaurant APIs call this so every query is scoped to it. */
    public long requireBusinessId() {
        if (businessId == null) throw ApiException.forbidden("This needs a business account.");
        return businessId;
    }
}
