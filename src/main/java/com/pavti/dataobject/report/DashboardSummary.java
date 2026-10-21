package com.pavti.dataobject.report;

import com.pavti.dataobject.common.ChartPoint;
import com.pavti.dataobject.order.OrderListItem;
import java.math.BigDecimal;
import java.util.List;

/** Home screen numbers. ownOnly = staff without SALES_VIEW see only their own work. */
public record DashboardSummary(
        boolean ownOnly,
        BigDecimal salesToday,
        long billsToday,
        BigDecimal collectedToday,
        BigDecimal pendingAmount,
        long pendingCount,
        long openOrders,
        long busyTables,
        long totalTables,
        List<ChartPoint> last14Days,
        List<ChartPoint> topItems,
        List<OrderListItem> recentOrders) {}
