package com.ispf.server.object;

import com.ispf.server.bootstrap.PlatformBootstrap;
import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.object.pubsub.ObjectChangePublicationService;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.platform.ClusterPlatformBootstrapService;
import com.ispf.server.persistence.ObjectNodeRepository;
import com.ispf.server.persistence.ObjectVariableRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * F-08 (code analysis 2026-09): per-path follower syncs must not serialize on the
 * ObjectManager instance; a whole-tree reload must still exclude them.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ObjectManagerTreeSyncLockTest {

    private static final String PATH_A = "root.devices.pump-a";
    private static final String PATH_B = "root.devices.pump-b";

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
                nodeRepository,
                variableRepository,
                mapper,
                platformBootstrap,
                bootstrapProperties,
                publicationService,
                self,
                configAuditService,
                clusterBootstrapService,
                driverTelemetryService,
                bootstrapFacade,
                treeCrudService,
                variableService,
                loadSyncService,
                metadataService
        );
        executor = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void syncsOnDifferentPathsRunConcurrently() throws Exception {
        CountDownLatch aEntered = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        doAnswer(invocation -> {
            String path = invocation.getArgument(0);
            if (PATH_A.equals(path)) {
                aEntered.countDown();
                releaseA.await(5, TimeUnit.SECONDS);
            }
            return null;
        }).when(loadSyncService).reloadPathFromDatabase(anyString());

        Future<?> syncA = executor.submit(() -> objectManager.reloadPathFromDatabase(PATH_A));
        assertThat(aEntered.await(2, TimeUnit.SECONDS)).as("sync A entered").isTrue();

        // While A is parked inside the sync, B on another path must complete on its own.
        Future<?> syncB = executor.submit(() -> objectManager.reloadPathFromDatabase(PATH_B));
        syncB.get(2, TimeUnit.SECONDS);

        assertThat(syncA.isDone()).as("A still parked").isFalse();
        releaseA.countDown();
        syncA.get(2, TimeUnit.SECONDS);
    }

    @Test
    void syncsOnSamePathStillSerialize() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean firstCall = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (firstCall.getAndSet(false)) {
                firstEntered.countDown();
                releaseFirst.await(5, TimeUnit.SECONDS);
            }
            return null;
        }).when(loadSyncService).syncVariableFromDatabase(anyString(), anyString());

        Future<?> first = executor.submit(() -> objectManager.syncVariableFromDatabase(PATH_A, "x"));
        assertThat(firstEntered.await(2, TimeUnit.SECONDS)).isTrue();

        Future<?> second = executor.submit(() -> objectManager.syncVariableFromDatabase(PATH_A, "y"));
        Thread.sleep(150);
        assertThat(second.isDone()).as("same-path sync must wait").isFalse();

        releaseFirst.countDown();
        first.get(2, TimeUnit.SECONDS);
        second.get(2, TimeUnit.SECONDS);
    }

    @Test
    void fullReloadWaitsForInFlightPathSyncs() throws Exception {
        CountDownLatch aEntered = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        doAnswer(invocation -> {
            aEntered.countDown();
            releaseA.await(5, TimeUnit.SECONDS);
            return null;
        }).when(loadSyncService).reloadPathFromDatabase(anyString());

        Future<?> syncA = executor.submit(() -> objectManager.reloadPathFromDatabase(PATH_A));
        assertThat(aEntered.await(2, TimeUnit.SECONDS)).isTrue();

        Future<?> reload = executor.submit(() -> objectManager.reloadFromDatabase());
        Thread.sleep(150);
        assertThat(reload.isDone()).as("full reload must wait for path sync").isFalse();

        releaseA.countDown();
        syncA.get(2, TimeUnit.SECONDS);
        reload.get(2, TimeUnit.SECONDS);
    }

    @Test
    void removePathIsReentrantFromWithinPathSync() throws Exception {
        // ObjectTreeLoadSyncService.reloadPathFromDatabase calls back removePathFromMemoryIfPresent
        // on the same thread — both lock levels must be re-entrant.
        doAnswer(invocation -> {
            objectManager.removePathFromMemoryIfPresent(invocation.getArgument(0));
            return null;
        }).when(loadSyncService).reloadPathFromDatabase(anyString());

        Future<?> sync = executor.submit(() -> objectManager.reloadPathFromDatabase(PATH_A));
        sync.get(2, TimeUnit.SECONDS);
    }
}
