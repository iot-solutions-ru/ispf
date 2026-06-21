package com.ispf.server.event;

import com.ispf.core.object.ObjectEvent;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.model.DataRecord;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import com.ispf.server.api.dto.DataRecordPayloadResolver;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.EventHistoryRepository;
import com.ispf.server.persistence.entity.EventHistoryEntity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class EventService {

    private final ObjectManager objectManager;
    private final EventHistoryRepository eventHistoryRepository;
    private final ObjectEntityMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    public EventService(
            ObjectManager objectManager,
            EventHistoryRepository eventHistoryRepository,
            ObjectEntityMapper mapper,
            ApplicationEventPublisher eventPublisher
    ) {
        this.objectManager = objectManager;
        this.eventHistoryRepository = eventHistoryRepository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ObjectEvent fire(String objectPath, String eventName, DataRecordPayloadRequest payload) {
        PlatformObject node = objectManager.require(objectPath);
        EventDescriptor descriptor = Optional.ofNullable(node.events().get(eventName))
                .orElseThrow(() -> new IllegalArgumentException("Unknown event: " + eventName));

        DataRecord resolvedPayload = DataRecordPayloadResolver.resolve(descriptor.payloadSchema(), payload);
        ObjectEvent event = ObjectEvent.of(objectPath, eventName, descriptor.level(), resolvedPayload);
        persist(event);
        eventPublisher.publishEvent(ObjectChangeEvent.eventFired(objectPath, eventName));
        return event;
    }

    @Transactional
    public ObjectEvent fire(String objectPath, String eventName, DataRecord payload) {
        return fire(objectPath, eventName, DataRecordPayloadResolver.fromRecord(payload));
    }

    @Transactional(readOnly = true)
    public List<ObjectEvent> list(String objectPath, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
        List<EventHistoryEntity> entities = objectPath == null || objectPath.isBlank()
                ? eventHistoryRepository.findAllByOrderByOccurredAtDesc(page)
                : eventHistoryRepository.findByObjectPathOrderByOccurredAtDesc(objectPath, page);
        return entities.stream().map(this::toObjectEvent).toList();
    }

    private void persist(ObjectEvent event) {
        EventHistoryEntity entity = new EventHistoryEntity();
        entity.setId(event.id());
        entity.setObjectPath(event.objectPath());
        entity.setEventName(event.eventName());
        entity.setLevel(event.level().name());
        entity.setPayloadJson(mapper.writeDataRecord(event.payload()));
        entity.setOccurredAt(event.timestamp());
        eventHistoryRepository.save(entity);
    }

    private ObjectEvent toObjectEvent(EventHistoryEntity entity) {
        DataRecord payload = mapper.readDataRecord(entity.getPayloadJson());
        if (payload == null) {
            payload = DataRecord.empty(
                    com.ispf.core.model.DataSchema.builder("eventPayload").build()
            );
        }
        return new ObjectEvent(
                entity.getId(),
                entity.getObjectPath(),
                entity.getEventName(),
                EventLevel.valueOf(entity.getLevel()),
                payload,
                entity.getOccurredAt()
        );
    }
}
