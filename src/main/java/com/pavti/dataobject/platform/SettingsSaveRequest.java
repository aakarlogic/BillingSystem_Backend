package com.pavti.dataobject.platform;

import java.math.BigDecimal;

/** Owner Settings page. */
public record SettingsSaveRequest(
        String businessName,
        String mobileNo,
        String email,
        String addressLine,
        String gstin,
        String billPrefix,
        BigDecimal defaultTaxRate,
        BigDecimal serviceChargePct,
        String serviceChargeLabel,
        String billFooterNote,
        String defaultLanguage) {}
