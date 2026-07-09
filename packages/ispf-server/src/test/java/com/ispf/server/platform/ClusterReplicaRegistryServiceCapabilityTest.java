package com.ispf.server.platform;

import com.ispf.server.config.ReplicaCapability;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterReplicaRegistryServiceCapabilityTest {

    @Test
    void capabilitiesContainMatchesExternalName() {
        assertTrue(ClusterReplicaRegistryService.capabilitiesContain(
                "http-public,ws,replica-sync,analytics", ReplicaCapability.ANALYTICS));
    }

    @Test
    void capabilitiesContainIgnoresBlank() {
        assertFalse(ClusterReplicaRegistryService.capabilitiesContain("", ReplicaCapability.ANALYTICS));
        assertFalse(ClusterReplicaRegistryService.capabilitiesContain(null, ReplicaCapability.ANALYTICS));
    }
}
