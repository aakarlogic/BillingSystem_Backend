package com.pavti.dataobject.customer;

import java.math.BigDecimal;

public record CustomerListItem(
        long customerId,
        long businessId,
        String customerName,
        String mobileNo,
        String email,
        String addressLine,
        String gstin,
        long billCount,
        BigDecimal billed,
        BigDecimal balance) {}
