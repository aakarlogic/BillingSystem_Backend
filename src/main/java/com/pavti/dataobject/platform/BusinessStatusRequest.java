package com.pavti.dataobject.platform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BusinessStatusRequest(
        @NotNull(message = "Choose a business.") Long businessId,
        @NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED|PENDING", message = "Choose a valid status.") String status,
        String reason) {}
