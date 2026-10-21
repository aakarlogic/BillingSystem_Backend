package com.pavti.dataobject.order;

import java.math.BigDecimal;
import java.util.List;

/** Full order/bill with its lines (React app: types/order.ts Order). */
public record Order(
        long orderId,
        long businessId,
        String billNo,
        String orderType,
        Long tableId,
        String tableName,
        Long customerId,
        String customerName,
        String orderStatus,
        String businessDate,
        BigDecimal subTotal,
        String discountType,
        BigDecimal discountValue,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        List<TaxLine> taxLines,
        BigDecimal serviceChargePct,
        BigDecimal serviceChargeAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal balanceAmount,
        String notes,
        List<OrderItem> items,
        String waiterName,
        String billedDate,
        String paidDate,
        String cancelReason,
        String insertedDate) {

    public static Order of(OrderListItem o, List<OrderItem> items, List<TaxLine> taxLines) {
        return new Order(o.orderId(), o.businessId(), o.billNo(), o.orderType(), o.tableId(), o.tableName(), o.customerId(),
                o.customerName(), o.orderStatus(), o.businessDate(), o.subTotal(), o.discountType(), o.discountValue(),
                o.discountAmount(), o.taxAmount(), taxLines, o.serviceChargePct(), o.serviceChargeAmount(), o.totalAmount(),
                o.paidAmount(), o.balanceAmount(), o.notes(), items, o.waiterName(), o.billedDate(), o.paidDate(),
                o.cancelReason(), o.insertedDate());
    }
}
