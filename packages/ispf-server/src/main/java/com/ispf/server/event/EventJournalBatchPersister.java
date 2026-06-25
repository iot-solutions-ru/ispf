package com.ispf.server.event;

import com.ispf.server.persistence.entity.EventHistoryEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Service
public class EventJournalBatchPersister {

    private static final String INSERT_SQL = """
            INSERT INTO event_history (id, object_path, event_name, level, payload_json, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final EventHistoryRecordCounter recordCounter;

    public EventJournalBatchPersister(JdbcTemplate jdbcTemplate, EventHistoryRecordCounter recordCounter) {
        this.jdbcTemplate = jdbcTemplate;
        this.recordCounter = recordCounter;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistBatch(List<EventHistoryEntity> batch) {
        if (batch.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                INSERT_SQL,
                batch,
                batch.size(),
                (statement, entity) -> {
                    statement.setString(1, entity.getId());
                    statement.setString(2, entity.getObjectPath());
                    statement.setString(3, entity.getEventName());
                    statement.setString(4, entity.getLevel());
                    statement.setString(5, entity.getPayloadJson());
                    statement.setTimestamp(6, Timestamp.from(entity.getOccurredAt()));
                }
        );
        recordCounter.recordPersisted(batch.size());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistOne(EventHistoryEntity entity) {
        jdbcTemplate.update(
                INSERT_SQL,
                entity.getId(),
                entity.getObjectPath(),
                entity.getEventName(),
                entity.getLevel(),
                entity.getPayloadJson(),
                Timestamp.from(entity.getOccurredAt())
        );
        recordCounter.recordPersisted(1);
    }
}
