package com.pavti.dataobject.user;

import java.util.List;

/** userId empty = new person. password empty on edit = keep the old one. */
public record UserSaveRequest(
        Long userId,
        Long businessId,
        String roleCode,
        String fullName,
        String mobileNo,
        String email,
        String password,
        List<String> permissions) {}
