package com.pavti.dataobject.order;

import java.math.BigDecimal;
import java.util.List;

/** Create or update an OPEN order; makeBill = also give it a bill number. */
public record OrderSaveRequest(
        Long orderId,
        String orderType,
        Long tableId,
        Long customerId,
        List<OrderLineRequest> lines,
        String discountType,
        BigDecimal discountValue,
        String discountReason,
        BigDecimal serviceChargePct,
        String notes,
        boolean makeBill) {}
