package uk.co.sangharsh.logtime.plugin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MergedSession {
    private final BigDecimal startTime;
    private BigDecimal endTime;
    private final String project;
    private final List<Heartbeat> heartbeats;

    public MergedSession(Heartbeat firstHeartbeat) {
        this.startTime = firstHeartbeat.timestamp;
        this.endTime = firstHeartbeat.timestamp;
        this.project = firstHeartbeat.project;
        this.heartbeats = new ArrayList<>();
        this.heartbeats.add(firstHeartbeat);
    }

    public void updateEndTime(BigDecimal newEndTime) {
        this.endTime = newEndTime;
    }

    public void addHeartbeat(Heartbeat heartbeat) {
        heartbeats.add(heartbeat);
    }

    public List<Heartbeat> getHeartbeats() {
        return heartbeats;
    }

    public int getHeartbeatCount() {
        return heartbeats.size();
    }

    /**
     * The most recent heartbeat in this session. Its {@code failCount} is the retry counter used
     * when this session's worklog fails to post, and it is carried back into the queue so the
     * counter survives restarts.
     */
    public Heartbeat getLastHeartbeat() {
        return heartbeats.get(heartbeats.size() - 1);
    }

    /**
     * Calculates duration in seconds. Timestamps are in epoch seconds.
     */
    public long getDurationInSeconds() {
        BigDecimal differenceSeconds = this.endTime.subtract(this.startTime);
        long seconds = differenceSeconds.longValue();
        return seconds <= 0 ? 0 : seconds;
    }

    public boolean hasElapsed() {
        return this.endTime.compareTo(this.startTime) > 0;
    }

    public long getStartEpochMillis() {
        return this.startTime.multiply(BigDecimal.valueOf(1000)).longValue();
    }

    /**
     * Formats the start timestamp (epoch seconds) into a Jira ISO 8601 string.
     */
    public String getStartTimeISO() {
        OffsetDateTime dateTime = OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(getStartEpochMillis()), ZoneId.systemDefault());
        return dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public BigDecimal getEndTime() { return endTime; }
    public String getProject() { return project; }
}
