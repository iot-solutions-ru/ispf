package com.ispf.server.application.data;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxDeliveryTest {

    @Mock
    private Producer<String, String> producer;

    @Test
    void outageKeepsTheRowUntilKafkaAcceptsIt() {
        CompletableFuture<RecordMetadata> failed = new CompletableFuture<>();
        failed.completeExceptionally(new KafkaException("broker down"));
        RecordMetadata ok = new RecordMetadata(new TopicPartition("plant.erp", 0), 0, 0, 0L, 0, 0);
        when(producer.send(any())).thenReturn(failed, CompletableFuture.completedFuture(ok));
        doNothing().when(producer).close(any(Duration.class));

        JdbcTemplate jdbc = new JdbcTemplate(new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build());
        ApplicationMessageRelay relay = new ApplicationMessageRelay(jdbc);
        relay.ensureTables();
        String id = relay.enqueue("plant", "erp-kafka", "m-kafka", "{\"qty\":1}");
        KafkaOutboxDelivery delivery = new KafkaOutboxDelivery(
                producer,
                Map.of("erp-kafka", "plant.erp"),
                Duration.ofSeconds(2)
        );

        assertEquals(0, relay.relayOnce(delivery.topics().keySet(), delivery));
        assertEquals("PENDING", relay.status(id));
        assertEquals(1, relay.relayOnce(delivery.topics().keySet(), delivery));
        assertEquals("SENT", relay.status(id));

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer, times(2)).send(captor.capture());
        ProducerRecord<String, String> record = captor.getAllValues().get(1);
        assertEquals("plant.erp", record.topic());
        assertEquals("m-kafka", record.key());
        assertEquals("{\"qty\":1}", record.value());
        delivery.close();
    }

    @Test
    void unknownConnectorIsRejected() {
        doNothing().when(producer).close(any(Duration.class));
        KafkaOutboxDelivery delivery = new KafkaOutboxDelivery(
                producer,
                Map.of("erp-kafka", "plant.erp"),
                Duration.ofSeconds(2)
        );
        assertThrows(IllegalStateException.class, () -> delivery.deliver("plant", "other", "m1", "{}"));
        delivery.close();
    }
}
