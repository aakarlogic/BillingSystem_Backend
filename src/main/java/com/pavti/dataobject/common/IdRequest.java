package com.pavti.dataobject.common;

import jakarta.validation.constraints.NotNull;

public record IdRequest(@NotNull(message = "Id is required.") Long id) {}
