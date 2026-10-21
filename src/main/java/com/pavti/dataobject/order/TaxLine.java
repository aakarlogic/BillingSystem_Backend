package com.pavti.dataobject.order;

import java.math.BigDecimal;

/** GST of one rate, for the bill footer (GST 5% on 720 = 36). */
public record TaxLine(BigDecimal taxRate, BigDecimal taxable, BigDecimal tax) {}
