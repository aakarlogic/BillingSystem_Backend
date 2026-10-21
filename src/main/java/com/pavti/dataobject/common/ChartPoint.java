package com.pavti.dataobject.common;

import java.math.BigDecimal;

/** One bar / row in a chart. */
public record ChartPoint(String label, BigDecimal value) {}
