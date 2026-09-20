package com.ispf.server.object;

import com.ispf.server.bootstrap.PlatformBootstrap;
import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.object.pubsub.ObjectChangePublicationService;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.ObjectNodeRepository;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.platform.ClusterPlatformBootstrapService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * F-08 (code analysis 2026-09) — measurement behind the lock change in {@link ObjectManager}.
 *
 * <p>Cluster followers apply one {@code reloadPathFromDatabase(path)} per structural event. Before F-08 the
 * method was {@code synchronized} on the ObjectManager instance, so N followers' syncs for N different
 * devices ran one after another. This test replays the same workload twice against the same object:
 * <ul>
 *   <li><b>baseline</b> — every sync wrapped in {@code synchronized (objectManager)}, the pre-F-08 shape;</li>
 *   <li><b>per-path</b> — the production path (RW lock + per-path monitor).</li>
 * </ul>
 * The mocked repository sync sleeps {@value #DB_LATENCY_MS} ms to stand in for a JDBC round-trip, which is
 * what dominates a real sync. Absolute numbers are synthetic; the <em>ratio</em> is the claim: with
 * {@value #THREADS} distinct paths the per-path variant must be at least {@value #MIN_SPEEDUP}× faster.
 * Ideal is ≈{@value #THREADS}×; the floor is deliberately loose for shared CI runners.
 *
 * <p>Run alone for the numbers:
 * {@code ./gradlew :packages:ispf-server:test --tests "*ObjectManagerPathSyncBenchmarkTest" -i}
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ObjectManagerPathSyncBenchmarkTest {

    static final int THREADS = 8;
    static final int SYNCS_PER_THREAD = 20;
    static final long DB_LATENCY_MS = 2;
    static final double MIN_SPEEDUP = 2.0;

    @Mock private ObjectNodeRepository nodeRepository;
    @Mock private ObjectVariableRepository variableRepository;
    @Mock private ObjectEntityMapper mapper;
    @Mock private PlatformBootstrap platformBootstrap;
    @Mock private BootstrapProperties bootstrapProperties;
    @Mock private ObjectChangePublicationService publicationService;
    @Mock private ObjectProvider<ObjectManager> self;
    @Mock private ObjectConfigAuditService configAuditService;
    @Mock private ObjectProvider<ClusterPlatformBootstrapService> clusterBootstrapService;
    @Mock private ObjectProvider<DriverTelemetryService> driverTelemetryService;
    @Mock private ObjectTreeBootstrapFacade bootstrapFacade;
    @Mock private TreeCrudService treeCrudService;
    @Mock private ObjectVariableService variableService;
    @Mock private ObjectTreeLoadSyncService loadSyncService;
    @Mock private ObjectMetadataService metadataService;

    private ObjectManager objectManager;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        objectManager = new ObjectManager(
                nodeRepository, variableRepository, mapper, platformBootstrap, bootstrapProperties,
                publicationService, self, configAuditService, clusterBootstrapService, driverTelemetryService,
                bootstrapFacade, treeCrudService, variableService, loadSyncService, metadataService
        );
        doAnswer(invocation -> {
            LockSupport.parkNanos(Duration.ofMillis(DB_LATENCY_MS).toNanos());
            return null;
        }).when(loadSyncService).reloadPathFromDatabase(anyString());
        executor = Executors.newFixedThreadPool(THREADS);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void perPathLockingBeatsInstanceMonitorOnDistinctPaths() throws Exception {
        // warm-up (class loading, mock stubbing, thread start) — discarded
        run(this::perPathSync);
        run(this::instanceMonitorSync);

        long baselineNanos = Long.MAX_VALUE;
        long perPathNanos = Long.MAX_VALUE;
        for (int round = 0; round < 3; round++) {
            baselineNanos = Math.min(baselineNanos, run(this::instanceMonitorSync));
            perPathNanos = Math.min(perPathNanos, run(this::perPathSync));
        }
        double speedup = (double) baselineNanos / perPathNanos;
        long serialWorkMs = THREADS * SYNCS_PER_THREAD * DB_LATENCY_MS;
        System.out.printf(
                "F-08 ObjectManager path sync: %d threads x %d syncs x %d ms simulated DB (%d ms of work) — "
                        + "instance monitor (pre-F-08) %.1f ms, per-path lock %.1f ms, speedup %.1fx%n",
                THREADS, SYNCS_PER_THREAD, DB_LATENCY_MS, serialWorkMs,
                baselineNanos / 1e6, perPathNanos / 1e6, speedup);

        assertThat(baselineNanos)
                .as("instance monitor serialises the work — cannot beat the serial sum")
                .isGreaterThanOrEqualTo(Duration.ofMillis(serialWorkMs).toNanos() * 9 / 10);
        assertThat(speedup)
                .as("per-path locking must let %d distinct paths sync in parallel", THREADS)
                .isGreaterThanOrEqualTo(MIN_SPEEDUP);
    }

    /** Pre-F-08 shape: {@code public synchronized void reloadPathFromDatabase(String)}. */
    private void instanceMonitorSync(String path) {
        synchronized (objectManager) {
            objectManager.reloadPathFromDatabase(path);
        }
    }

    private void perPathSync(String path) {
        objectManager.reloadPathFromDatabase(path);
    }

    private long run(Consumer<String> sync) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            String path = "root.devices.follower-" + t;
            futures.add(executor.submit(() -> {
                start.await();
                for (int i = 0; i < SYNCS_PER_THREAD; i++) {
                    sync.accept(path);
                }
                return null;
            }));
        }
        long started = System.nanoTime();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        return System.nanoTime() - started;
    }
}
