package com.pavti.dataobject.order;

import java.math.BigDecimal;

/** One line from the order screen. unitPrice is ignored for staff (the menu price is used). */
public record OrderLineRequest(Long itemId, String itemName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate) {}
