package com.pavti.dataobject.report;

import java.math.BigDecimal;

/** Row of sp_report_items. */
public record ItemSalesRow(String itemName, BigDecimal quantity, BigDecimal sales) {}
