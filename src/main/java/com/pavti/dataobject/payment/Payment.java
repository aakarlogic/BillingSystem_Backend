package com.pavti.dataobject.payment;

import java.math.BigDecimal;

/** One money entry: PAYMENT (cash/UPI received) or REFUND (v_payment_list). */
public record Payment(
        long paymentId,
        long orderId,
        String billNo,
        String paymentType,
        String paymentMode,
        BigDecimal amount,
        BigDecimal cashReceived,
        BigDecimal changeReturned,
        String reason,
        boolean isRefunded,
        String receivedByName,
        Long receivedBy,
        String receivedDate,
        String businessDate) {

    /** + for a payment, - for a refund. */
    public BigDecimal signedAmount() {
        return "REFUND".equals(paymentType) ? amount.negate() : amount;
    }
}
