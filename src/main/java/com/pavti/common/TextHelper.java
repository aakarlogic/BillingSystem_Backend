package com.pavti.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Small input clean-up helpers used by the BAL classes. */
public final class TextHelper {

    private TextHelper() {}

    public static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    /** "+91 98200 11111" → "9820011111". Only digits are kept; India +91 prefix removed. */
    public static String mobile(String s) {
        String digits = clean(s).replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        return digits;
    }

    public static boolean isMobile(String s) {
        return s != null && s.matches("[0-9]{10}");
    }

    public static boolean isEmail(String s) {
        return s != null && s.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    public static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }
}
