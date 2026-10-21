package com.pavti.dataobject.table;

/** Row of sp_table_get_by_id. */
public record TableRow(long tableId, long businessId, String tableName, String tableStatus, Long currentOrderId) {}
