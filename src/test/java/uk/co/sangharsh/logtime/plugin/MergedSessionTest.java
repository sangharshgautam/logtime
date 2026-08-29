package uk.co.sangharsh.logtime.plugin;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MergedSessionTest {

    private static Heartbeat heartbeat(long epochSeconds) {
        Heartbeat h = new Heartbeat();
        h.timestamp = BigDecimal.valueOf(epochSeconds);
        h.project = "TEST-123";
        return h;
    }

    @Test
    public void singleHeartbeatHasNoElapsedTime() {
        MergedSession session = new MergedSession(heartbeat(1_700_000_000L));
        assertEquals(0L, session.getDurationInSeconds());
        assertFalse(session.hasElapsed());
    }

    @Test
    public void durationCalculatedInSeconds() {
        MergedSession session = new MergedSession(heartbeat(1_700_000_000L));
        session.updateEndTime(BigDecimal.valueOf(1_700_000_120L));
        // 120 seconds of elapsed time, NOT 0.12s
        assertEquals(120L, session.getDurationInSeconds());
        assertTrue(session.hasElapsed());
    }

    @Test
    public void backdatedEndTimeHasNoElapsedTime() {
        MergedSession session = new MergedSession(heartbeat(1_700_000_100L));
        session.updateEndTime(BigDecimal.valueOf(1_700_000_050L));
        assertEquals(0L, session.getDurationInSeconds());
        assertFalse(session.hasElapsed());
    }

    @Test
    public void startTimeIsoRoundedToEpochMillis() {
        MergedSession session = new MergedSession(heartbeat(1_700_000_000L));
        // The ISO timestamp should represent the same instant as 1_700_000_000 seconds,
        // i.e. 1_700_000_000_000 ms since the epoch (timezone-independent check).
        String iso = session.getStartTimeISO();
        long epochMillis = Instant.parse(iso).toEpochMilli();
        assertEquals(1_700_000_000_000L, epochMillis);
    }
}
