package uk.co.sangharsh.logtime.plugin;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class HeartbeatStoreTest {

    private Path tempDir;
    private Path queueFile;
    private HeartbeatStore store;

    private int idCounter = 0;

    private Heartbeat heartbeat(String project, String ts) {
        Heartbeat h = new Heartbeat();
        h.id = "hb-" + (++idCounter);
        h.project = project;
        h.entity = "/tmp/" + project + "/File.java";
        h.timestamp = new BigDecimal(ts);
        return h;
    }

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("logtime-test");
        queueFile = tempDir.resolve("heartbeats.jsonl");
        store = new HeartbeatStore(queueFile);
    }

    @After
    public void tearDown() throws Exception {
        store.close();
        if (Files.exists(tempDir)) {
            try (java.util.stream.Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) { } });
            }
        }
    }

    @Test
    public void appendAndLoadPersistsHeartbeats() {
        store.append(heartbeat("PROJECT-1", "1000"));
        store.append(heartbeat("PROJECT-1", "1001"));

        List<Heartbeat> loaded = store.load();
        assertEquals(2, loaded.size());
        assertEquals("PROJECT-1", loaded.get(0).project);
        assertEquals("PROJECT-1", loaded.get(1).project);
        assertEquals(new BigDecimal("1000"), loaded.get(0).timestamp);
        assertEquals(new BigDecimal("1001"), loaded.get(1).timestamp);
    }

    @Test
    public void loadDoesNotRemoveEntries() {
        store.append(heartbeat("PROJECT-1", "1000"));
        store.load();
        store.load();
        assertEquals(1, store.pendingCount());
        assertEquals(1, store.load().size());
    }

    @Test
    public void removeIdsOnlyRemovesThoseIds() {
        Heartbeat a = heartbeat("PROJECT-1", "1000");
        Heartbeat b = heartbeat("PROJECT-1", "1001");
        Heartbeat c = heartbeat("PROJECT-2", "1002");
        store.append(a);
        store.append(b);
        store.append(c);

        // Remove only the first and third lines (e.g. the second failed and must be kept).
        assertTrue(store.removeIds(java.util.Arrays.asList(a.id, c.id)));

        List<Heartbeat> remaining = store.load();
        assertEquals(1, remaining.size());
        assertEquals("PROJECT-1", remaining.get(0).project);
        assertEquals(b.id, remaining.get(0).id);
    }

    @Test
    public void removeIdsPreservesOrderOfNonRemoved() {
        Heartbeat a = heartbeat("PROJECT-1", "1000");
        Heartbeat b = heartbeat("PROJECT-1", "1001");
        Heartbeat c = heartbeat("PROJECT-1", "1002");
        store.append(a);
        store.append(b);
        store.append(c);

        assertTrue(store.removeIds(java.util.Collections.singletonList(b.id)));

        List<Heartbeat> remaining = store.load();
        assertEquals(2, remaining.size());
        assertEquals(a.id, remaining.get(0).id);
        assertEquals(c.id, remaining.get(1).id);
    }

    @Test
    public void removeIdsAllEntriesDeletesFile() throws Exception {
        Heartbeat a = heartbeat("PROJECT-1", "1000");
        Heartbeat b = heartbeat("PROJECT-1", "1001");
        store.append(a);
        store.append(b);

        assertTrue(store.removeIds(java.util.Arrays.asList(a.id, b.id)));
        assertFalse(Files.exists(queueFile));
        assertEquals(0, store.load().size());
    }

    @Test
    public void removeIdsEmptyOrNullIsNoOp() {
        Heartbeat a = heartbeat("PROJECT-1", "1000");
        store.append(a);

        assertFalse(store.removeIds(java.util.Collections.emptyList()));
        assertFalse(store.removeIds(null));
        assertEquals(1, store.load().size());
    }

    @Test
    public void incrementFailCountPersistsAcrossReload() {
        Heartbeat a = heartbeat("PROJECT-1", "1000");
        store.append(a);

        assertEquals(1, store.incrementFailCount(a.id));
        assertEquals(2, store.incrementFailCount(a.id));

        // Simulate a restart: a brand new store instance reading the same file sees the counter.
        HeartbeatStore reloaded = new HeartbeatStore(queueFile);
        assertEquals(3, reloaded.incrementFailCount(a.id));
        Heartbeat loaded = reloaded.load().get(0);
        assertEquals(Integer.valueOf(3), loaded.failCount);
    }

    @Test
    public void incrementFailCountUnknownIdReturnsMinusOne() {
        store.append(heartbeat("PROJECT-1", "1000"));
        assertEquals(-1, store.incrementFailCount("does-not-exist"));
    }

    @Test
    public void trailingPartialLineIsIgnoredAndTrimmed() throws Exception {
        store.append(heartbeat("PROJECT-1", "1000"));
        // Simulate a crash mid-append: a partial JSON line with no trailing newline.
        Files.write(queueFile, "{\"project\":\"PROJECT-1\"".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.APPEND);

        List<Heartbeat> loaded = store.load();
        assertEquals(1, loaded.size());
        assertEquals("PROJECT-1", loaded.get(0).project);

        // The partial line should have been trimmed so the queue is clean.
        List<String> lines = Files.readAllLines(queueFile);
        assertEquals(1, lines.size());
    }

    @Test
    public void malformedLineIsSkippedWithoutLosingOthers() throws Exception {
        store.append(heartbeat("PROJECT-1", "1000"));
        Files.write(queueFile, "this is not json\n".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.APPEND);
        store.append(heartbeat("PROJECT-1", "1002"));

        List<Heartbeat> loaded = store.load();
        assertEquals(2, loaded.size());
        assertEquals("PROJECT-1", loaded.get(0).project);
        assertEquals(new BigDecimal("1002"), loaded.get(1).timestamp);
    }

    @Test
    public void appendAfterCrashIncompleteLineStillWorks() throws Exception {
        store.append(heartbeat("PROJECT-1", "1000"));
        Files.write(queueFile, "{\"project\":\"PROJECT-1\"".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.APPEND);

        // Subsequent append must not corrupt the file (truncates the broken tail first).
        store.append(heartbeat("PROJECT-2", "1002"));

        List<Heartbeat> loaded = store.load();
        assertEquals(2, loaded.size());
        assertEquals("PROJECT-1", loaded.get(0).project);
        assertEquals("PROJECT-2", loaded.get(1).project);
    }

    @Test
    public void clearRemovesEverything() {
        store.append(heartbeat("PROJECT-1", "1000"));
        store.append(heartbeat("PROJECT-1", "1001"));

        assertTrue(store.clear());
        assertFalse(Files.exists(queueFile));
        assertEquals(0, store.load().size());
    }

    @Test
    public void claimMarksOwnerAndClaimedAt() {
        Heartbeat a = heartbeat("PROJECT-1", "1000");
        Heartbeat b = heartbeat("PROJECT-1", "1001");
        store.append(a);
        store.append(b);

        long now = System.currentTimeMillis();
        assertTrue(store.claim(java.util.Arrays.asList(a.id, b.id), "instance-1", now));

        List<Heartbeat> loaded = store.load();
        assertEquals("instance-1", loaded.get(0).owner);
        assertEquals(Long.valueOf(now), loaded.get(0).claimedAt);
        assertEquals("instance-1", loaded.get(1).owner);
    }

    @Test
    public void loadUnclaimedExcludesFreshClaimsAndIncludesUnowned() {
        Heartbeat a = heartbeat("PROJECT-1", "1000");
        Heartbeat b = heartbeat("PROJECT-1", "1001");
        store.append(a);
        store.append(b);

        long now = System.currentTimeMillis();
        store.claim(java.util.Arrays.asList(a.id), "instance-1", now);

        // a was claimed at `now`; with the cutoff at `now` its claim is fresh (not below the
        // cutoff), so it is excluded. b is unowned (included).
        List<Heartbeat> unclaimed = store.loadUnclaimed(now);
        assertEquals(1, unclaimed.size());
        assertEquals(b.id, unclaimed.get(0).id);
    }

    @Test
    public void loadUnclaimedIncludesStaleClaimsFromOtherOwners() throws Exception {
        Heartbeat a = heartbeat("PROJECT-1", "1000");
        store.append(a);

        long staleCutoff = System.currentTimeMillis();
        // Claim with an old timestamp, simulating an owner that crashed long ago.
        store.claim(java.util.Arrays.asList(a.id), "instance-crashed", staleCutoff - 10_000_000L);

        // staleOlderThanMs way in the future: the claim looks stale, so it is eligible again.
        List<Heartbeat> unclaimed = store.loadUnclaimed(staleCutoff + 100);
        assertEquals(1, unclaimed.size());
        assertEquals(a.id, unclaimed.get(0).id);
    }

    @Test
    public void releaseClaimClearsOwnerSoItCanBeReclaimed() {
        Heartbeat a = heartbeat("PROJECT-1", "1000");
        store.append(a);

        long now = System.currentTimeMillis();
        store.claim(java.util.Arrays.asList(a.id), "instance-1", now);
        assertTrue(store.releaseClaim(java.util.Arrays.asList(a.id)));

        List<Heartbeat> unclaimed = store.loadUnclaimed(now + 1);
        assertEquals(1, unclaimed.size());
        assertEquals(a.id, unclaimed.get(0).id);
        assertNull(store.load().get(0).owner);
    }

    @Test
    public void pendingCountTracksAppendsAndRemoves() {
        Heartbeat a = heartbeat("PROJECT-1", "1000");
        Heartbeat b = heartbeat("PROJECT-1", "1001");
        Heartbeat c = heartbeat("PROJECT-2", "1002");
        store.append(a);
        store.append(b);
        store.append(c);
        assertEquals(3, store.pendingCount());

        store.removeIds(java.util.Arrays.asList(b.id));
        assertEquals(2, store.pendingCount());

        store.clear();
        assertEquals(0, store.pendingCount());
    }

    @Test
    public void appendStaysFastAsQueueGrows() throws Exception {
        // Simulate a large existing backlog written directly (bypassing the store).
        StringBuilder big = new StringBuilder(1 << 21);
        for (int i = 0; i < 500_000; i++) big.append(i).append('\n');
        Files.write(queueFile, big.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // append must stay O(1) in file size: a constant-size tail check plus a single append
        // write. A whole-file scan or rewrite per append (the old behavior) would take far longer.
        long start = System.nanoTime();
        store.append(heartbeat("PROJECT-1", "9999999999"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue("append on a large queue took " + elapsedMs + "ms", elapsedMs < 2000);

        assertEquals(500_001L, Files.lines(queueFile).count());
    }
}
