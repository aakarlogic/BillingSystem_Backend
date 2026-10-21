package com.pavti.dataobject.table;

import java.math.BigDecimal;

/** Table on the floor screen with its current order numbers (sp_table_get_floor). */
public record TableCard(
        long tableId,
        long businessId,
        String tableName,
        String areaName,
        int seats,
        String tableStatus,
        Long currentOrderId,
        BigDecimal orderTotal,
        BigDecimal orderBalance,
        BigDecimal itemCount,
        String since) {}
