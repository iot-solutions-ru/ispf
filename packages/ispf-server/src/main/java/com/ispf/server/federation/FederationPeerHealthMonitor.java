package com.ispf.server.federation;

import com.ispf.core.model.DataRecord;
import com.ispf.server.event.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically evaluates federation peer health and fires platform events on RED transitions (S27).
 */
@Component
public class FederationPeerHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(FederationPeerHealthMonitor.class);

    private final FederationPeerStore peerStore;
    private final FederationPeerHealthService peerHealthService;
    private final EventService eventService;
    private final boolean enabled;
    private final Map<UUID, FederationPeerHealthLevel> lastLevels = new ConcurrentHashMap<>();

    public FederationPeerHealthMonitor(
            FederationPeerStore peerStore,
            FederationPeerHealthService peerHealthService,
            EventService eventService,
            @Value("${ispf.federation.health.monitor-enabled:true}") boolean enabled
    ) {
        this.peerStore = peerStore;
        this.peerHealthService = peerHealthService;
        this.eventService = eventService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${ispf.federation.health.monitor-interval-ms:60000}")
    void scanPeerHealth() {
        if (!enabled) {
            return;
        }
        for (FederationPeer peer : peerStore.listAll()) {
            if (!peer.enabled()) {
                lastLevels.remove(peer.id());
                continue;
            }
            FederationPeerHealth health = peerHealthService.health(peer.id());
            FederationPeerHealthLevel previous = lastLevels.put(peer.id(), health.level());
            if (previous == health.level()) {
                continue;
            }
            if (health.level() == FederationPeerHealthLevel.RED) {
                fire(FederationPeerHealthBootstrap.EVENT_PEER_HEALTH_DEGRADED, peer, health);
            } else if (previous == FederationPeerHealthLevel.RED && health.level() == FederationPeerHealthLevel.GREEN) {
                fire(FederationPeerHealthBootstrap.EVENT_PEER_HEALTH_RECOVERED, peer, health);
            }
        }
    }

    private void fire(String eventName, FederationPeer peer, FederationPeerHealth health) {
        try {
            eventService.fire(
                    FederationPaths.FEDERATION_ROOT,
                    eventName,
                    DataRecord.single(
                            FederationPeerHealthBootstrap.PEER_HEALTH_PAYLOAD,
                            Map.of(
                                    "peerId", peer.id().toString(),
                                    "peerName", peer.name(),
                                    "level", health.level().name(),
                                    "summary", health.summary() != null ? health.summary() : ""
                            )
                    )
            );
            log.info("Federation health event {} for peer {} ({})", eventName, peer.name(), health.summary());
        } catch (Exception ex) {
            log.warn("Failed to fire federation health event {} for peer {}: {}", eventName, peer.name(), ex.getMessage());
        }
    }
}
