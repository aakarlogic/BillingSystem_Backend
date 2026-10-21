package com.pavti.dataobject.customer;

/** Row of sp_customer_get_by_id. */
public record CustomerRow(long customerId, long businessId, String customerName) {}
