package com.pavti.dataobject.menu;

import java.math.BigDecimal;

/** itemId empty = new dish. */
public record MenuItemSaveRequest(
        Long itemId,
        Long categoryId,
        String itemCode,
        String itemName,
        String imageUrl,
        String unitName,
        BigDecimal price,
        BigDecimal taxRate,
        Boolean isVeg,
        Boolean isAvailable) {}
