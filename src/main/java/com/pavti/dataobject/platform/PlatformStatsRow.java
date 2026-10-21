package com.pavti.dataobject.platform;

import java.math.BigDecimal;

/** Row of sp_platform_stats. */
public record PlatformStatsRow(long businesses, long activeBusinesses, long users, long bills, BigDecimal monthlyRevenue,
                               BigDecimal collected) {}
