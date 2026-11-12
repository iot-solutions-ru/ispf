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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void routesLocallyWhenOwnershipDisabled() {
        StubOwnership ownership = new StubOwnership();
        ownership.enabled = false;
        ownership.holds = false;
        DriverWriteRouter router = new DriverWriteRouter(ownership);
        AtomicBoolean invoked = new AtomicBoolean();

        DriverWriteRouter.WriteResult result = router.route(
                "root.platform.devices.pump-01",
                "cmdOpen",
                VALUE,
                () -> {
                    invoked.set(true);
                    return LOCAL_STATUS;
                }
        );

        assertInstanceOf(DriverWriteRouter.WriteResult.Local.class, result);
        assertSame(LOCAL_STATUS, ((DriverWriteRouter.WriteResult.Local) result).status());
        assertTrue(invoked.get());
    }

    @Test
    void routesLocallyWhenThisReplicaHoldsLock() {
        StubOwnership ownership = new StubOwnership();
        ownership.enabled = true;
        ownership.holds = true;
        DriverWriteRouter router = new DriverWriteRouter(ownership);
        AtomicBoolean invoked = new AtomicBoolean();

        DriverWriteRouter.WriteResult result = router.route(
                "root.platform.devices.pump-01",
                "cmdOpen",
                VALUE,
                () -> {
                    invoked.set(true);
                    return LOCAL_STATUS;
                }
        );

        assertInstanceOf(DriverWriteRouter.WriteResult.Local.class, result);
        assertTrue(invoked.get());
    }

    @Test
    void forwardContainsOwnerDevicePointAndValue() {
        StubOwnership ownership = new StubOwnership();
        ownership.enabled = true;
        ownership.holds = false;
        ownership.owner = Optional.of("replica-east-2");
        DriverWriteRouter router = new DriverWriteRouter(ownership);
        AtomicBoolean invoked = new AtomicBoolean();

        DriverWriteRouter.WriteResult result = router.route(
                "root.platform.devices.pump-01",
                "cmdOpen",
                VALUE,
                () -> {
                    invoked.set(true);
                    return LOCAL_STATUS;
                }
        );

        assertFalse(invoked.get());
        assertInstanceOf(DriverWriteRouter.WriteResult.Forward.class, result);
        DriverWriteRouter.ForwardRequest forward =
                ((DriverWriteRouter.WriteResult.Forward) result).forwardRequest();
        assertEquals("replica-east-2", forward.ownerId());
        assertEquals("root.platform.devices.pump-01", forward.devicePath());
        assertEquals("cmdOpen", forward.pointId());
        assertSame(VALUE, forward.value());
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
    }
}
