package com.pavti.dataobject.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Name is required.") @Size(max = 100) String fullName,
        String mobileNo,
        @Pattern(regexp = "mr|hi|en", message = "Choose a language.") String preferredLanguage) {}
