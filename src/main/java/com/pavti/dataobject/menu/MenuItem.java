package com.pavti.dataobject.menu;

import java.math.BigDecimal;

/** One dish (React app: types/menuItem.ts MenuItem). */
public record MenuItem(
        long itemId,
        long businessId,
        Long categoryId,
        String itemCode,
        String itemName,
        String imageUrl,
        String unitName,
        BigDecimal price,
        BigDecimal taxRate,
        boolean isVeg,
        boolean isAvailable) {}
