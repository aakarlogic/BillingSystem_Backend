package com.pavti.dataobject.user;

/** Row of sp_user_get_by_id. */
public record UserBrief(long userId, Long businessId, String roleCode, String fullName, String mobileNo, boolean isActive) {}
