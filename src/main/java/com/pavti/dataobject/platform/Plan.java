package com.pavti.dataobject.platform;

import java.math.BigDecimal;

public record Plan(long planId, String planCode, String planName, String description, BigDecimal monthlyPrice, int maxUsers,
                   Integer maxTables) {}
