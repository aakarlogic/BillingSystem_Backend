package com.pavti.dataobject.order;

import java.math.BigDecimal;

public record OrderItem(
        long orderItemId,
        Long itemId,
        String itemName,
        String unitName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount,
        BigDecimal discountAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal lineTotal) {}
