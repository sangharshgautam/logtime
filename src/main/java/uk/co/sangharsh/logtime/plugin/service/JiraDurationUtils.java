package uk.co.sangharsh.logtime.plugin.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class JiraDurationUtils {
    private static final DateTimeFormatter JIRA_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxx");
    public static String convertMilliToJiraFormat(BigDecimal milliTime) {
        if (milliTime == null) return null;

        // 1. Extract the long millisecond value safely
        long millis = milliTime.longValue()*1000;

        // 2. Define the target time zone
        ZoneId localZone = ZoneId.systemDefault();

        // 3. Map the absolute timeline point to UTC (or change to your local ZoneOffset)
        OffsetDateTime dateTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), localZone);

        // 4. Format to the strict payload string
        return dateTime.format(JIRA_FORMATTER);
    }


    public static long getJiraSeconds(BigDecimal startSec, BigDecimal endSec) {
        if (startSec == null || endSec == null) return 0;
        long seconds = endSec.subtract(startSec).longValue();
        return Math.max(0, seconds);
    }
    /**
     * Converts a start and end timestamp (in milliseconds) into a Jira-formatted duration string.
     * Maps to standard business time tracking settings (8-hour workdays, 5-day workweeks).
     *
     * @param startLong Start time epoch in milliseconds.
     * @param endLong   End time epoch in milliseconds.
     * @return A human-readable duration string (e.g., "1d 3h 15m").
     */
    public static String convertToJiraDuration(long startLong, long endLong) {
        // 1. Calculate the absolute difference in minutes
        long totalMinutes = (endLong - startLong) / 1000 / 60;

        // Jira requires a minimum work log of at least 1 minute
        if (totalMinutes < 1) {
            totalMinutes = 1;
        }

        // 2. Define standard Jira Time Tracking configuration constants
        final int MINS_IN_HOUR = 60;
        final int MINS_IN_DAY = 8 * MINS_IN_HOUR;   // 480 minutes
        final int MINS_IN_WEEK = 5 * MINS_IN_DAY;   // 2400 minutes

        // 3. Sequential integer division and modulo math
        long weeks = totalMinutes / MINS_IN_WEEK;
        totalMinutes %= MINS_IN_WEEK;

        long days = totalMinutes / MINS_IN_DAY;
        totalMinutes %= MINS_IN_DAY;

        long hours = totalMinutes / MINS_IN_HOUR;
        long minutes = totalMinutes % MINS_IN_HOUR;

        // 4. Cleanly assemble non-zero text components
        List<String> parts = new ArrayList<>();
        if (weeks > 0) parts.add(weeks + "w");
        if (days > 0) parts.add(days + "d");
        if (hours > 0) parts.add(hours + "h");
        if (minutes > 0) parts.add(minutes + "m");

        return String.join(" ", parts);
    }

    public static void main(String[] args) {
        // Example: 11 hours and 15 minutes of total elapsed time (675 minutes)
        // Under an 8-hour workday, this shifts into 1 day, 3 hours, and 15 minutes.
        long start = 1717610400000L;
        long end = 1717650900000L;

        String jiraString = convertToJiraDuration(start, end);
        System.out.println("Jira Duration String: " + jiraString);
        // Output: "1d 3h 15m"
    }
}
