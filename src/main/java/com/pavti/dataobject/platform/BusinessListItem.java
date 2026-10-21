package com.pavti.dataobject.platform;

import java.math.BigDecimal;

/** Row of the platform admin's business list (sp_business_get_all). */
public record BusinessListItem(
        long businessId,
        String businessCode,
        String businessName,
        String businessType,
        String ownerName,
        String mobileNo,
        String email,
        String addressLine,
        String gstin,
        String currencyCode,
        String defaultLanguage,
        String billPrefix,
        long nextBillNo,
        BigDecimal defaultTaxRate,
        BigDecimal serviceChargePct,
        String serviceChargeLabel,
        String billFooterNote,
        long planId,
        String status,
        String statusReason,
        String insertedDate,
        String planName,
        int maxUsers,
        long userCount,
        long billCount,
        BigDecimal collected) {}
