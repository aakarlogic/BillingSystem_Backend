package com.pavti.common;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * The restaurant's reporting day. The day closes at 4 AM, so a bill at
 * 1:30 AM still counts for the evening before (SRS: "business date").
 */
public final class BusinessDateHelper {

    private static final int DAY_CLOSE_HOUR = 4;

    private BusinessDateHelper() {}

    public static ZoneId zone(String timeZone) {
        try {
            return ZoneId.of(timeZone == null || timeZone.isBlank() ? "Asia/Kolkata" : timeZone);
        } catch (Exception e) {
            return ZoneId.of("Asia/Kolkata");
        }
    }

    public static LocalDate today(String timeZone) {
        return ZonedDateTime.now(zone(timeZone)).minusHours(DAY_CLOSE_HOUR).toLocalDate();
    }
}
