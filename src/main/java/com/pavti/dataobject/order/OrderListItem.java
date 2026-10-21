package com.pavti.dataobject.order;

import java.math.BigDecimal;

/** One order/bill as lists show it (v_order_list). */
public record OrderListItem(
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
        BigDecimal serviceChargePct,
        BigDecimal serviceChargeAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal balanceAmount,
        String notes,
        Long waiterUserId,
        String waiterName,
        Long billedBy,
        String billedDate,
        String paidDate,
        String cancelReason,
        String insertedDate,
        BigDecimal itemCount) {

    /** Counted as a sale (has a bill number and is not cancelled). */
    public boolean sale() {
        return switch (orderStatus) {
            case "BILLED", "PART_PAID", "PAID", "REFUNDED" -> true;
            default -> false;
        };
    }

    public boolean mine(long userId) {
        return (waiterUserId != null && waiterUserId == userId) || (billedBy != null && billedBy == userId);
    }
}
