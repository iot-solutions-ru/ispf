package com.ispf.server.driver;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Renews driver locks for active polls and reclaims expired locks on peer failure (ADR-0028).
 */
@Component
public class DriverOwnershipScheduler {

    private static final Logger log = LoggerFactory.getLogger(DriverOwnershipScheduler.class);

    private final DriverOwnershipService ownershipService;
    private final DriverRuntimeService driverRuntimeService;
    private final ObjectManager objectManager;

    public DriverOwnershipScheduler(
            DriverOwnershipService ownershipService,
            DriverRuntimeService driverRuntimeService,
            ObjectManager objectManager
    ) {
        this.ownershipService = ownershipService;
        this.driverRuntimeService = driverRuntimeService;
        this.objectManager = objectManager;
    }

    @Scheduled(fixedDelayString = "${ispf.cluster.driver-lock-renew-ms:10000}")
    public void renewActiveDriverLocks() {
        if (!ownershipService.isEnabled()) {
            return;
        }
        List<String> activePaths = new ArrayList<>(driverRuntimeService.activeDevicePaths());
        for (String devicePath : activePaths) {
            if (!ownershipService.renew(devicePath)) {
                log.warn("Lost driver ownership for {}, stopping local poll", devicePath);
                driverRuntimeService.stopLocal(devicePath, false);
            }
        }
    }

    @Scheduled(fixedDelayString = "${ispf.cluster.driver-failover-scan-ms:15000}")
    public void reclaimExpiredDriverLocks() {
        if (!ownershipService.isEnabled()) {
            return;
        }
        for (String devicePath : ownershipService.findExpiredDevicePaths()) {
            if (driverRuntimeService.isActiveLocally(devicePath)) {
                continue;
            }
            if (!shouldAutoReclaim(devicePath)) {
                continue;
            }
            if (!ownershipService.tryAcquire(devicePath)) {
                continue;
            }
            try {
                log.info("Reclaiming driver ownership for {} on replica {}", devicePath, ownershipService.instanceId());
                driverRuntimeService.startHeld(devicePath);
            } catch (Exception ex) {
                ownershipService.release(devicePath);
                log.debug("Failed to reclaim driver for {}: {}", devicePath, ex.getMessage());
            }
        }
    }

    private boolean shouldAutoReclaim(String devicePath) {
        try {
            PlatformObject node = objectManager.require(devicePath);
            if (node.type() != ObjectType.DEVICE) {
                return false;
            }
            return driverRuntimeService.readBinding(devicePath).isPresent();
        } catch (Exception ex) {
            return false;
        }
    }
}
