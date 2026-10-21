package com.pavti.dataobject.customer;

/** customerId empty = new customer. */
public record CustomerSaveRequest(Long customerId, String customerName, String mobileNo, String email, String addressLine,
                                  String gstin) {}
