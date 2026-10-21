package com.pavti.dataobject.auth;

import java.time.Instant;

public record SessionRow(long sessionId, long userId, Long businessId, Instant expiresAt) {}
