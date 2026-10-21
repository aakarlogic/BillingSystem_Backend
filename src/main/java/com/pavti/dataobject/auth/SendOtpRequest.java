package com.pavti.dataobject.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** purpose: REGISTER or RESET_PASSWORD */
public record SendOtpRequest(
        @NotBlank(message = "Enter your mobile number.") String mobileNo,
        @NotBlank @Pattern(regexp = "REGISTER|RESET_PASSWORD", message = "Unknown OTP purpose.") String purpose) {}
