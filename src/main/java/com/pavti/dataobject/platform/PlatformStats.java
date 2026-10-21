package com.pavti.dataobject.platform;

import com.pavti.dataobject.common.ChartPoint;
import java.math.BigDecimal;
import java.util.List;

public record PlatformStats(
        long businesses,
        long activeBusinesses,
        long suspendedBusinesses,
        long users,
        long bills,
        BigDecimal monthlyRevenue,
        BigDecimal collected,
        List<ChartPoint> byBusiness,
        List<ChartPoint> byPlan) {}
