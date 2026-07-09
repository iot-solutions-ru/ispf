package com.ispf.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplicaCapabilitySetTest {

    @Test
    void legacyRoleApiMapsToEdgeApi() {
        ReplicaCapabilitySet set = ReplicaCapabilitySet.resolve(null, "api", null);
        assertEquals(ReplicaProfile.EDGE_API, set.profile());
        assertTrue(set.has(ReplicaCapability.HTTP_PUBLIC));
        assertFalse(set.has(ReplicaCapability.DRIVERS));
    }

    @Test
    void ioProfileHasDriversNotJobs() {
        ReplicaCapabilitySet set = ReplicaCapabilitySet.resolve("io", null, null);
        assertTrue(set.has(ReplicaCapability.DRIVERS));
        assertTrue(set.has(ReplicaCapability.REPLICA_SYNC));
        assertFalse(set.has(ReplicaCapability.JOBS));
        assertFalse(set.has(ReplicaCapability.HTTP_PUBLIC));
    }

    @Test
    void computeProfileHasJobsNotDrivers() {
        ReplicaCapabilitySet set = ReplicaCapabilitySet.resolve("compute", null, null);
        assertTrue(set.has(ReplicaCapability.JOBS));
        assertFalse(set.has(ReplicaCapability.DRIVERS));
    }

    @Test
    void rejectsJobsAndDriversTogether() {
        assertThrows(IllegalArgumentException.class, () ->
                ReplicaCapabilitySet.resolve(null, null, "drivers,jobs"));
    }

    @Test
    void analyticsProfileHasAnalyticsSchedulersNotDrivers() {
        ReplicaCapabilitySet set = ReplicaCapabilitySet.resolve("analytics", null, null);
        assertEquals(ReplicaProfile.ANALYTICS, set.profile());
        assertTrue(set.has(ReplicaCapability.ANALYTICS));
        assertTrue(set.has(ReplicaCapability.REPLICA_SYNC));
        assertTrue(set.has(ReplicaCapability.SCHEDULERS));
        assertFalse(set.has(ReplicaCapability.DRIVERS));
        assertFalse(set.has(ReplicaCapability.HTTP_PUBLIC));
    }

    @Test
    void rejectsAnalyticsAndDriversTogether() {
        assertThrows(IllegalArgumentException.class, () ->
                ReplicaCapabilitySet.resolve(null, null, "analytics,drivers"));
    }

    @Test
    void hmiReadIsReadOnly() {
        ReplicaCapabilitySet set = ReplicaCapabilitySet.resolve("hmi-read", null, null);
        assertTrue(set.has(ReplicaCapability.HTTP_PUBLIC));
        assertTrue(set.has(ReplicaCapability.WS));
        assertFalse(set.has(ReplicaCapability.CONFIG_WRITE));
    }
}
