package com.pavti.dataobject.platform;

import java.math.BigDecimal;

/** planId empty = new plan. */
public record PlanSaveRequest(Long planId, String planCode, String planName, String description, BigDecimal monthlyPrice,
                              Integer maxUsers, Integer maxTables) {}
