package com.pavti.dataobject.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Enter your current password.") String currentPassword,
        @NotBlank @Size(min = 6, max = 100, message = "New password must be at least 6 characters.") String newPassword) {}
