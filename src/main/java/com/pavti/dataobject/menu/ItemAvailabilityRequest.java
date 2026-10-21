package com.pavti.dataobject.menu;

import jakarta.validation.constraints.NotNull;

public record ItemAvailabilityRequest(@NotNull(message = "Choose a dish.") Long itemId, boolean isAvailable) {}
