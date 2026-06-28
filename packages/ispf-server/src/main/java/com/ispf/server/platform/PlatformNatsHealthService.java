package com.ispf.server.platform;

import com.ispf.server.config.NatsProperties;
import com.ispf.server.workflow.NatsEventBridge;
import com.ispf.server.workflow.NatsJetStreamSupport;
import io.nats.client.JetStreamApiException;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.StreamInfo;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class PlatformNatsHealthService {

    private final NatsProperties properties;
    private final NatsEventBridge eventBridge;
    private final NatsJetStreamSupport jetStreamSupport;

    public PlatformNatsHealthService(
            NatsProperties properties,
            NatsEventBridge eventBridge,
            NatsJetStreamSupport jetStreamSupport
    ) {
        this.properties = properties;
        this.eventBridge = eventBridge;
        this.jetStreamSupport = jetStreamSupport;
    }

    public NatsHealth health() {
        boolean enabled = properties.enabled();
        boolean connected = eventBridge.isEnabled();
        String connectionError = null;
        if (enabled && !connected) {
            connectionError = "NATS enabled but connection is not available";
        }

        boolean jetStreamConfigured = enabled && properties.jetStreamEnabled();
        boolean jetStreamActive = jetStreamConfigured && jetStreamSupport.isActive();
        boolean streamReady = jetStreamActive && jetStreamSupport.isStreamReady();
        Long streamMessages = null;
        Long streamBytes = null;
        String consumerDurable = null;
        Long consumerPending = null;

        if (jetStreamActive) {
            try {
                StreamInfo streamInfo = jetStreamSupport.management()
                        .getStreamInfo(properties.jetStreamStreamName());
                streamMessages = streamInfo.getStreamState().getMsgCount();
                streamBytes = streamInfo.getStreamState().getByteCount();
                if (!streamReady) {
                    streamReady = true;
                }
            } catch (IOException | JetStreamApiException ex) {
                connectionError = ex.getMessage();
            }

            consumerDurable = properties.jetStreamReplicaConsumerPrefix() + properties.replicaId();
            if (properties.replicaEventsEnabled()) {
                try {
                    ConsumerInfo consumerInfo = jetStreamSupport.management()
                            .getConsumerInfo(properties.jetStreamStreamName(), consumerDurable);
                    consumerPending = consumerInfo.getNumPending();
                } catch (IOException | JetStreamApiException ex) {
                    consumerDurable = null;
                }
            }
        }

        return new NatsHealth(
                enabled,
                connected,
                enabled ? properties.url() : null,
                properties.replicaId(),
                properties.replicaEventsEnabled(),
                jetStreamConfigured,
                jetStreamActive,
                jetStreamConfigured ? properties.jetStreamStreamName() : null,
                streamReady,
                streamMessages,
                streamBytes,
                consumerDurable,
                consumerPending,
                connected,
                connectionError
        );
    }

    public record NatsHealth(
            boolean enabled,
            boolean connected,
            String url,
            String replicaId,
            boolean replicaEventsEnabled,
            boolean jetStreamEnabled,
            boolean jetStreamActive,
            String streamName,
            boolean streamReady,
            Long streamMessages,
            Long streamBytes,
            String consumerDurable,
            Long consumerPending,
            boolean publishNatsAvailable,
            String connectionError
    ) {
    }
}
