package com.pavti.dataobject.auth;

import java.time.Instant;

public record OtpRow(long otpId, String otpHash, Instant expiresAt, int attempts, Instant usedDate) {}
