package com.ispf.server.federation;

import com.ispf.server.object.ObjectChangeEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Polls subscribed federated object paths and publishes local object-change events for WebSocket clients.
 */
@Component
public class FederationSubscribePollService {

    private final FederationProxyService federationProxyService;
    private final ApplicationEventPublisher eventPublisher;
    private final Set<String> subscribedPaths = ConcurrentHashMap.newKeySet();
    private final Map<String, String> lastSnapshot = new ConcurrentHashMap<>();

    public FederationSubscribePollService(
            FederationProxyService federationProxyService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.federationProxyService = federationProxyService;
        this.eventPublisher = eventPublisher;
    }

    public void replaceSubscriptions(Set<String> paths) {
        subscribedPaths.clear();
        subscribedPaths.addAll(paths.stream()
                .filter(path -> path != null && FederationPaths.isCatalogMirrorPath(path))
                .toList());
        lastSnapshot.keySet().removeIf(path -> !subscribedPaths.contains(path));
    }

    @Scheduled(fixedDelayString = "${ispf.federation.subscribe-poll-ms:5000}")
    void pollSubscribedPaths() {
        for (String path : subscribedPaths) {
            federationProxyService.resolve(path).ifPresent(target -> {
                try {
                    var json = federationProxyService.proxyObject(target);
                    String snapshot = json == null ? "" : json.toString();
                    String previous = lastSnapshot.put(path, snapshot);
                    if (previous != null && !previous.equals(snapshot)) {
                        eventPublisher.publishEvent(ObjectChangeEvent.of(
                                com.ispf.server.object.ObjectChangeType.UPDATED,
                                path
                        ));
                    }
                } catch (RuntimeException ignored) {
                    // peer unavailable — skip until next tick
                }
            });
        }
    }
}
