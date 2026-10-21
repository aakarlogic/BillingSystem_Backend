package com.pavti.dataobject.user;

import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(@NotNull(message = "Choose a person.") Long userId, boolean isActive) {}
