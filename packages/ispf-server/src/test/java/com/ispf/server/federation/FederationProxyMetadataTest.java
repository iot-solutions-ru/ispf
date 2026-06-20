package com.ispf.server.federation;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FederationProxyMetadataTest {

    @Test
    void reappliesMetadataOnExistingReadOnlyProxyVariables() {
        PlatformObject node = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.federation.site-a.devices.demo-sensor-01",
                ObjectType.DEVICE,
                "Demo Sensor 01",
                "",
                null
        );
        UUID peerId = UUID.randomUUID();
        String remotePath = "root.platform.devices.demo-sensor-01";

        FederationProxyMetadata.applyTo(node, peerId, remotePath);
        assertTrue(FederationProxyMetadata.isProxy(node));

        assertDoesNotThrow(() -> FederationProxyMetadata.applyTo(node, peerId, remotePath));
        assertTrue(FederationProxyMetadata.remotePath(node).orElse("").equals(remotePath));
    }
}
