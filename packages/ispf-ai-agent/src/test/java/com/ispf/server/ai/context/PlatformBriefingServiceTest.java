package com.ispf.server.ai.context;

import com.ispf.driver.DriverMetadata;
import com.ispf.driver.DriverMaturity;
import com.ispf.server.application.bundle.ApplicationBundleSnapshotStore;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.cache.PlatformBriefingCacheEpoch;
import com.ispf.server.config.AiProperties;
import com.ispf.server.driver.DriverCatalog;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectTreePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformBriefingServiceTest {

    @Mock
    private ContextPackService contextPackService;
    @Mock
    private DriverCatalog driverCatalog;
    @Mock
    private ApplicationDataStore applicationDataStore;
    @Mock
    private ApplicationBundleSnapshotStore bundleSnapshotStore;
    @Mock
    private ObjectTreePort objectTreePort;
    @Mock
    private ObjectTree objectTree;

    private PlatformBriefingService briefingService;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        properties.setBriefingMaxChars(20_000);
        briefingService = new PlatformBriefingService(
                properties,
                contextPackService,
                driverCatalog,
                applicationDataStore,
                bundleSnapshotStore,
                objectTreePort,
                new ConcurrentMapCacheManager("platformBriefing"),
                new PlatformBriefingCacheEpoch(),
                Optional.empty()
        );
    }

    @Test
    void includesDriversExamplesAndLiveApps() {
        when(contextPackService.contextPackVersion()).thenReturn("ispf-0.7.8");
        when(contextPackService.loadPack()).thenReturn(Map.of(
                "exampleSummaries", List.of(Map.of(
                        "appId", "mes-reference",
                        "purpose", "MES reference",
                        "keySections", "functions, objects"
                )),
                "driverCatalog", List.of(
                        Map.of("driverId", "snmp"),
                        Map.of("driverId", "virtual"),
                        Map.of("driverId", "opcua")
                ),
                "featureIndex", List.of(Map.of("title", "Bundle deploy", "description", "manifest JSON"))
        ));
        when(driverCatalog.list()).thenReturn(List.of(
                new DriverMetadata("snmp", "SNMP", "1", "SNMP driver", "ISPF", Map.of(), DriverMaturity.PRODUCTION),
                new DriverMetadata("virtual", "Virtual", "1", "Simulator", "ISPF", Map.of(), DriverMaturity.PRODUCTION)
        ));
        when(applicationDataStore.listAllApps()).thenReturn(List.of(
                Map.of("app_id", "mes-reference", "display_name", "MES Reference")
        ));
        when(bundleSnapshotStore.findActive("mes-reference")).thenReturn(Optional.of(
                new ApplicationBundleSnapshotStore.BundleSnapshot(
                        java.util.UUID.randomUUID(),
                        "mes-reference",
                        "1.0.0",
                        "{}",
                        null,
                        java.time.Instant.now(),
                        true
                )
        ));
        when(objectTreePort.tree()).thenReturn(objectTree);
        when(objectTree.all()).thenReturn(List.of(
                new PlatformObject("dev1", "root.platform.devices.pump-1", ObjectType.DEVICE, "Pump 1", "", null)
        ));
        when(objectTree.findByPath("root.platform")).thenReturn(Optional.of(
                new PlatformObject("platform", "root.platform", ObjectType.PLATFORM, "Platform", "", null)
        ));
        when(objectTree.childrenOf("root")).thenReturn(List.of(
                new PlatformObject("id1", "root.platform", ObjectType.PLATFORM, "Platform", "", null)
        ));
        when(objectTree.childrenOf("root.platform")).thenReturn(List.of(
                new PlatformObject("id2", "root.platform.devices", ObjectType.DEVICES, "Devices", "", null)
        ));
        when(objectTree.childrenOf("root.platform.devices")).thenReturn(List.of(
                new PlatformObject("dev1", "root.platform.devices.pump-1", ObjectType.DEVICE, "Pump 1", "", null)
        ));

        String briefing = briefingService.buildBriefing("root", true);

        assertTrue(briefing.contains("Knowledge index"));
        assertTrue(briefing.contains("Drivers loaded here: 2"));
        assertTrue(briefing.contains("pack catalog has 3"));
        assertTrue(briefing.contains("snmp"));
        assertTrue(briefing.contains("virtual"));
        assertTrue(briefing.contains("mes-reference"));
        assertTrue(briefing.contains("Bundle deploy"));
        assertTrue(briefing.contains("root.platform"));
        assertTrue(briefing.contains("DEVICE: 1"));
        assertTrue(briefing.contains("pump-1"));
    }

    @Test
    void followUpTurnKeepsKnowledgeIndexWithoutFullStaticDump() {
        when(contextPackService.contextPackVersion()).thenReturn("ispf-0.7.8");
        when(contextPackService.loadPack()).thenReturn(Map.of(
                "exampleSummaries", List.of(Map.of("appId", "mes-reference", "purpose", "MES", "keySections", "x")),
                "driverCatalog", List.of(Map.of("driverId", "a"), Map.of("driverId", "b"), Map.of("driverId", "c")),
                "featureIndex", List.of(Map.of("title", "History", "description", "historian"))
        ));
        when(driverCatalog.list()).thenReturn(List.of(
                new DriverMetadata("virtual", "Virtual", "1", "Simulator", "ISPF", Map.of(), DriverMaturity.PRODUCTION)
        ));
        when(applicationDataStore.listAllApps()).thenReturn(List.of());
        when(objectTreePort.tree()).thenReturn(objectTree);
        when(objectTree.all()).thenReturn(List.of());
        when(objectTree.findByPath("root.platform")).thenReturn(Optional.empty());
        when(objectTree.childrenOf("root")).thenReturn(List.of());

        String briefing = briefingService.buildBriefing("root", false);

        assertTrue(briefing.contains("Knowledge index"));
        assertTrue(briefing.contains("Drivers loaded here: 1"));
        assertTrue(briefing.contains("pack catalog has 3"));
        assertTrue(briefing.contains("driverIds (1 of 1 loaded"));
        assertTrue(briefing.contains("virtual"));
        assertTrue(briefing.contains("SINGLETON or INSTANCE"));
        assertTrue(!briefing.contains("### Drivers (driverId | name | maturity)"));
        assertTrue(!briefing.contains("### Reference examples"));
    }
}
