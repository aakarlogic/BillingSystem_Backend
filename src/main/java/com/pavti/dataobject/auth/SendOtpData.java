package com.pavti.dataobject.auth;

/** devOtp is filled only when pavti.otp.dev-mode = true (no SMS in development). */
public record SendOtpData(boolean sent, int validMinutes, String devOtp) {}
