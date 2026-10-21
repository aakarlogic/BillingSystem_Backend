package com.pavti.dataobject.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Owner signs up a new restaurant (after /Auth/SendOtp). */
public record RegisterRequest(
        @NotBlank(message = "Restaurant name is required.") @Size(max = 150) String businessName,
        @Pattern(regexp = "RESTAURANT|CAFE|SHOP|SERVICE", message = "Choose a business type.") String businessType,
        @NotBlank(message = "Your name is required.") @Size(max = 100) String ownerName,
        @NotBlank(message = "Mobile number is required.") String mobileNo,
        @Email(message = "Email is not valid.") String email,
        @NotBlank @Size(min = 6, max = 100, message = "Password must be at least 6 characters.") String password,
        @NotBlank(message = "Enter the OTP sent to your mobile.") String otp) {}
