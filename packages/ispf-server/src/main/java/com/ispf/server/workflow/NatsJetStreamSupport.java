package com.ispf.server.workflow;

import com.ispf.server.config.NatsProperties;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.MessageHandler;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * Optional JetStream stream for durable automation event fan-out between replicas (0014).
 */
@Component
public class NatsJetStreamSupport {

    private static final Logger log = LoggerFactory.getLogger(NatsJetStreamSupport.class);
    static final String REPLICA_EVENTS_SUBJECT = "ispf.events.>";

    private final NatsProperties properties;
    private final JetStream jetStream;
    private final JetStreamManagement management;
    private volatile boolean streamReady;

    public NatsJetStreamSupport(NatsProperties properties, NatsEventBridge eventBridge) {
        this.properties = properties;
        Connection connection = eventBridge.connection();
        JetStream js = null;
        JetStreamManagement jsm = null;
        if (properties.enabled() && properties.jetStreamEnabled() && connection != null) {
            try {
                js = connection.jetStream();
                jsm = connection.jetStreamManagement();
            } catch (IOException ex) {
                log.warn("JetStream init failed ({}). Durable replica fan-out disabled.", ex.getMessage());
            }
        }
        this.jetStream = js;
        this.management = jsm;
    }

    public boolean isActive() {
        return jetStream != null && management != null;
    }

    public void ensureStream() throws IOException, JetStreamApiException {
        if (!isActive()) {
            return;
        }
        StreamConfiguration configuration = StreamConfiguration.builder()
                .name(properties.jetStreamStreamName())
                .subjects(REPLICA_EVENTS_SUBJECT)
                .storageType(StorageType.File)
                .retentionPolicy(RetentionPolicy.Limits)
                .maxAge(Duration.ofHours(properties.jetStreamMaxAgeHours()))
                .build();
        try {
            management.getStreamInfo(properties.jetStreamStreamName());
            management.updateStream(configuration);
        } catch (JetStreamApiException ex) {
            if (ex.getErrorCode() == 404) {
                management.addStream(configuration);
            } else {
                throw ex;
            }
        }
        streamReady = true;
        log.info(
                "JetStream stream ready: name={}, subjects={}, maxAgeHours={}",
                properties.jetStreamStreamName(),
                REPLICA_EVENTS_SUBJECT,
                properties.jetStreamMaxAgeHours()
        );
    }

    public void publishReplicaEvent(String subject, byte[] payload) {
        if (!isActive()) {
            return;
        }
        if (!streamReady) {
            try {
                ensureStream();
            } catch (IOException | JetStreamApiException ex) {
                log.warn("JetStream stream not ready: {}", ex.getMessage());
                return;
            }
        }
        try {
            jetStream.publish(subject, payload);
        } catch (IOException | JetStreamApiException ex) {
            log.warn("Failed to publish JetStream replica event to {}: {}", subject, ex.getMessage());
        }
    }

    public JetStreamSubscription subscribeReplicaEvents(Dispatcher dispatcher, MessageHandler handler)
            throws IOException, JetStreamApiException {
        if (!isActive()) {
            return null;
        }
        ensureStream();
        String durable = properties.jetStreamReplicaConsumerPrefix() + properties.replicaId();
        ConsumerConfiguration consumerConfiguration = ConsumerConfiguration.builder()
                .durable(durable)
                .deliverPolicy(DeliverPolicy.New)
                .filterSubject(REPLICA_EVENTS_SUBJECT)
                .ackPolicy(AckPolicy.Explicit)
                .build();
        PushSubscribeOptions options = PushSubscribeOptions.builder()
                .stream(properties.jetStreamStreamName())
                .durable(durable)
                .configuration(consumerConfiguration)
                .build();
        return jetStream.subscribe(
                REPLICA_EVENTS_SUBJECT,
                dispatcher,
                message -> {
                    handler.onMessage(message);
                    message.ack();
                },
                true,
                options
        );
    }
}
