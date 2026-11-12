package com.ispf.server.driver;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverWriteRouterTest {

    private static final DataRecord VALUE = DataRecord.single(
            DataSchema.builder("v").field("value", FieldType.BOOLEAN).build(),
            Map.of("value", true)
    );

    private static final DriverRuntimeService.DriverRuntimeStatus LOCAL_STATUS =
            new DriverRuntimeService.DriverRuntimeStatus(
            "root.platform.devices.pump-01",
            "virtual-demo",
            "RUNNING",
            true,
            1000,
            null,
            "FULL",
            0
    );

  private static final String DEVICE = "root.platform.devices.pump-01";

    @Test
    void routesLocallyWhenOwnershipDisabled() {
        StubOwnership ownership = new StubOwnership();
        ownership.enabled = false;
        ownership.holds = false;
        DriverWriteRouter router = new DriverWriteRouter(ownership);
        AtomicBoolean invoked = new AtomicBoolean();

        DriverRuntimeService.DriverRuntimeStatus result = router.route(
                DEVICE,
                "cmdOpen",
                VALUE,
                () -> {
                    invoked.set(true);
                    return LOCAL_STATUS;
                }
        );

        assertSame(LOCAL_STATUS, result);
        assertTrue(invoked.get());
    }

    @Test
    void routesLocallyWhenThisReplicaHoldsLock() {
        StubOwnership ownership = new StubOwnership();
        ownership.enabled = true;
        ownership.holds = true;
        DriverWriteRouter router = new DriverWriteRouter(ownership);
        AtomicBoolean invoked = new AtomicBoolean();

        DriverRuntimeService.DriverRuntimeStatus result = router.route(
                DEVICE,
                "cmdOpen",
                VALUE,
                () -> {
                    invoked.set(true);
                    return LOCAL_STATUS;
                }
        );

        assertSame(LOCAL_STATUS, result);
        assertTrue(invoked.get());
    }

    @Test
    void rejectsWriteWhenAnotherReplicaHoldsLock() {
        StubOwnership ownership = new StubOwnership();
        ownership.enabled = true;
        ownership.holds = false;
        ownership.owner = Optional.of("replica-east-2");
        DriverWriteRouter router = new DriverWriteRouter(ownership);
        AtomicBoolean invoked = new AtomicBoolean();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> router.route(
                DEVICE,
                "cmdOpen",
                VALUE,
                () -> {
                    invoked.set(true);
                    return LOCAL_STATUS;
                }
        ));

        assertTrue(ex.getMessage().contains("replica-east-2"));
        assertTrue(ex.getMessage().contains(DEVICE));
    }

    @Test
    void unknownOwnerWhenNoActiveLease() {
        StubOwnership ownership = new StubOwnership();
        ownership.enabled = true;
        ownership.holds = false;
        ownership.owner = Optional.empty();
        DriverWriteRouter router = new DriverWriteRouter(ownership);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> router.route(
                DEVICE,
                "cmdOpen",
                VALUE,
                () -> LOCAL_STATUS
        ));

        assertTrue(ex.getMessage().contains("unknown"));
    }

    @Test
    void forwardsToTheLeaseOwnerWhenNatsForwarderIsAvailable() {
        StubOwnership ownership = new StubOwnership();
        ownership.enabled = true;
        ownership.holds = false;
        ownership.owner = Optional.of("replica-east-2");
        AtomicBoolean invoked = new AtomicBoolean();
        DriverWriteForwarder forwarder = new DriverWriteForwarder() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public DriverRuntimeService.DriverRuntimeStatus forward(
                    String ownerReplicaId,
                    String devicePath,
                    String pointId,
                    DataRecord value
            ) {
                assertTrue("replica-east-2".equals(ownerReplicaId));
                assertTrue(DEVICE.equals(devicePath));
                assertTrue("cmdOpen".equals(pointId));
                return LOCAL_STATUS;
            }
        };
        DriverWriteRouter router = new DriverWriteRouter(ownership, () -> forwarder);

        DriverRuntimeService.DriverRuntimeStatus result = router.route(
                DEVICE,
                "cmdOpen",
                VALUE,
                () -> {
                    invoked.set(true);
                    return LOCAL_STATUS;
                }
        );

        assertSame(LOCAL_STATUS, result);
        assertTrue(!invoked.get());
    }

    private static final class StubOwnership extends DriverOwnershipService {
        boolean enabled = true;
        boolean holds;
        Optional<String> owner = Optional.of("peer");

        StubOwnership() {
            super(
                    null,
                    new ClusterProperties(
                            true,
                            true,
                            30,
                            10,
                            15,
                            10,
                            30,
                            true,
                            2,
                            8,
                            4,
                            6,
                            500,
                            true,
                            true,
                            500,
                            true,
                            "",
                            "",
                            "all",
                            true,
                            2000,
                            2,
                            true,
                            1,
                            8,
                            50,
                            6,
                            500,
                            1800
                    ),
                    new NatsProperties(
                            false,
                            "nats://localhost:4222",
                            false,
                            "replica-a",
                            false,
                            "ispf-automation",
                            24,
                            "ispf-replica-",
                            65536,
                            true,
                            2,
                            8,
                            50,
                            6,
                            -1,
                            30
                    )
            );
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public boolean holdsLock(String devicePath) {
            return holds;
        }

        @Override
        public Optional<String> findOwner(String devicePath) {
            return owner;
        }

        @Override
        public String instanceId() {
            return "replica-a";
        }
    }
}
