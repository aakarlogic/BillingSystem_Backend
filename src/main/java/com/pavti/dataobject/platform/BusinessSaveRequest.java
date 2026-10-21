package com.pavti.dataobject.platform;

/** Platform admin adds or edits a business. owner* fields are used only when adding. */
public record BusinessSaveRequest(
        Long businessId,
        String businessName,
        String businessType,
        String mobileNo,
        String email,
        String addressLine,
        String gstin,
        Long planId,
        String billPrefix,
        String ownerName,
        String ownerMobileNo,
        String ownerPassword) {}
