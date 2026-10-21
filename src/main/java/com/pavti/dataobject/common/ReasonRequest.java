package com.pavti.dataobject.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cancel / refund / pause: needs a reason for the audit log. */
public record ReasonRequest(
        @NotNull(message = "Id is required.") Long id,
        @NotBlank(message = "Write a reason.") @Size(max = 255) String reason) {}
