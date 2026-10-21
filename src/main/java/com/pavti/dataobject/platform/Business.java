package com.pavti.dataobject.platform;

import java.math.BigDecimal;

/** One business with its bill settings (React app: types/business.ts Business). */
public record Business(
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
        String timeZone) {}
