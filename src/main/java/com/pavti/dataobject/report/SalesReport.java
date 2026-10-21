package com.pavti.dataobject.report;

import com.pavti.dataobject.common.ChartPoint;
import com.pavti.dataobject.order.OrderListItem;
import java.math.BigDecimal;
import java.util.List;

public record SalesReport(
        long billCount,
        BigDecimal grossSales,
        BigDecimal subTotal,
        BigDecimal discount,
        BigDecimal tax,
        BigDecimal serviceCharge,
        BigDecimal received,
        BigDecimal refunded,
        BigDecimal cashTotal,
        BigDecimal upiTotal,
        long cancelledCount,
        BigDecimal cancelledValue,
        BigDecimal unpaidTotal,
        List<ChartPoint> days,
        List<ChartPoint> topByValue,
        List<ChartPoint> topByQty,
        List<ChartPoint> byStaff,
        List<OrderListItem> unpaid,
        List<OrderListItem> cancelled) {}
