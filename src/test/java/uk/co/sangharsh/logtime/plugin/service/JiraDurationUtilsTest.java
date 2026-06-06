package uk.co.sangharsh.logtime.plugin.service;

import org.junit.Assert;
import org.junit.Test;

public class JiraDurationUtilsTest {
    @Test
    public void shouldConvertToJiraDuration() {
        // Example: 11 hours and 15 minutes of total elapsed time (675 minutes)
        // Under an 8-hour workday, this shifts into 1 day, 3 hours, and 15 minutes.
        long start = 1717610400000L;
        long end = 1717650900000L;

        String jiraString = JiraDurationUtils.convertToJiraDuration(start, end);
        System.out.println("Jira Duration String: " + jiraString);
        // Output: "1d 3h 15m"
        Assert.assertEquals("1d 3h 15m", jiraString);
    }
}
