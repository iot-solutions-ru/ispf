package com.ispf.server.event;

import com.ispf.core.object.ObjectEvent;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.model.DataRecord;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import com.ispf.server.api.dto.DataRecordPayloadResolver;
import com.ispf.server.application.catalog.EventCatalogPayloadValidator;
import com.ispf.server.config.EventJournalProperties;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.entity.EventHistoryEntity;
import com.ispf.server.eventfilter.EventFilterMatcher;
import com.ispf.server.eventfilter.EventFilterObjectService;
import com.ispf.server.eventfilter.EventFilterObjectService.EventFilterDefinition;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.server.object.pubsub.ObjectChangePublicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class EventService {

    private final ObjectManager objectManager;
    private final EventJournalStore eventJournalStore;
    private final ObjectEntityMapper mapper;
    private final ObjectChangePublicationService publicationService;
    private final EventCatalogPayloadValidator catalogPayloadValidator;
    private final EventJournalAsyncWriter eventJournalAsyncWriter;
    private final RecentEventCache recentEventCache;
    private final EventJournalProperties eventJournalProperties;
    private final AutomationMetricsRecorder automationMetricsRecorder;
    private final EventTimestampValidator eventTimestampValidator;
    private final EventFilterObjectService eventFilterObjectService;
    private final EventFilterMatcher eventFilterMatcher;

    public EventService(
            ObjectManager objectManager,
            EventJournalStore eventJournalStore,
            ObjectEntityMapper mapper,
            ObjectChangePublicationService publicationService,
            EventCatalogPayloadValidator catalogPayloadValidator,
            EventJournalAsyncWriter eventJournalAsyncWriter,
            RecentEventCache recentEventCache,
            EventJournalProperties eventJournalProperties,
            AutomationMetricsRecorder automationMetricsRecorder,
            EventTimestampValidator eventTimestampValidator,
            EventFilterObjectService eventFilterObjectService,
            EventFilterMatcher eventFilterMatcher
    ) {
        this.objectManager = objectManager;
        this.eventJournalStore = eventJournalStore;
        this.mapper = mapper;
        this.publicationService = publicationService;
        this.catalogPayloadValidator = catalogPayloadValidator;
        this.eventJournalAsyncWriter = eventJournalAsyncWriter;
        this.recentEventCache = recentEventCache;
        this.eventJournalProperties = eventJournalProperties;
        this.automationMetricsRecorder = automationMetricsRecorder;
        this.eventTimestampValidator = eventTimestampValidator;
        this.eventFilterObjectService = eventFilterObjectService;
        this.eventFilterMatcher = eventFilterMatcher;
    }

    /**
     * Hot-path fire for automation (alerts). No surrounding transaction — journal is async.
     */
    public ObjectEvent fireAutomation(String objectPath, String eventName, DataRecord payload) {
        return fireInternal(
                objectPath,
                eventName,
                DataRecordPayloadResolver.fromRecord(payload),
                null,
                AutomationMetricsRecorder.EventFireSource.ALERT,
                null
        );
    }

    /**
     * Hot-path fire for high-rate driver ingress ({@code EVENT_JOURNAL_ONLY}). No HTTP, no transaction.
     */
    public ObjectEvent fireIngress(String objectPath, String eventName, DataRecord payload, Instant occurredAt) {
        return fireInternal(
                objectPath,
                eventName,
                DataRecordPayloadResolver.fromRecord(payload),
                null,
                AutomationMetricsRecorder.EventFireSource.INGRESS,
                occurredAt
        );
    }

    /** HTTP/API fire — no surrounding transaction; journal and automation reactions are async. */
    public ObjectEvent fire(String objectPath, String eventName, DataRecordPayloadRequest payload) {
        return fireInternal(objectPath, eventName, payload, null, AutomationMetricsRecorder.EventFireSource.API, null);
    }

    public ObjectEvent fire(String objectPath, String eventName, DataRecordPayloadRequest payload, String appId) {
        return fire(objectPath, eventName, payload, appId, null);
    }

    public ObjectEvent fire(
            String objectPath,
            String eventName,
            DataRecordPayloadRequest payload,
            String appId,
            Instant occurredAt
    ) {
        return fireInternal(
                objectPath,
                eventName,
                payload,
                appId,
                AutomationMetricsRecorder.EventFireSource.API,
                occurredAt
        );
    }

    public ObjectEvent fire(String objectPath, String eventName, DataRecord payload) {
        return fireInternal(
                objectPath,
                eventName,
                DataRecordPayloadResolver.fromRecord(payload),
                null,
                AutomationMetricsRecorder.EventFireSource.API,
                null
        );
    }

    public ObjectEvent fire(
            String objectPath,
            String eventName,
            DataRecord payload,
            AutomationMetricsRecorder.EventFireSource source
    ) {
        return fireInternal(
                objectPath,
                eventName,
                DataRecordPayloadResolver.fromRecord(payload),
                null,
                source,
                null
        );
    }

    public ObjectEvent fire(
            String objectPath,
            String eventName,
            DataRecordPayloadRequest payload,
            AutomationMetricsRecorder.EventFireSource source
    ) {
        return fireInternal(objectPath, eventName, payload, null, source, null);
    }

    @Transactional(readOnly = true)
    public List<ObjectEvent> list(String objectPath, int limit) {
        return list(objectPath, limit, null);
    }

    /**
     * Lists recent events, optionally applying a saved event filter (BL-174).
     *
     * @param filterPath path under {@code root.platform.event-filters.*}, or null/blank for unfiltered
     */
    @Transactional(readOnly = true)
    public List<ObjectEvent> list(String objectPath, int limit, String filterPath) {
        int capped = Math.max(1, Math.min(limit, 200));
        EventFilterDefinition filter = resolveFilter(filterPath);
        // Over-fetch when filtering so pattern/severity cuts still return a full page.
        int fetchLimit = filter != null ? Math.min(1000, capped * 10) : capped;
        boolean globalQuery = objectPath == null || objectPath.isBlank();
        List<ObjectEvent> fromCache = recentEventCache.isEnabled()
                ? recentEventCache.query(objectPath, fetchLimit)
                : List.of();
        if (!eventJournalProperties.isEnabled()) {
            return applyFilter(fromCache, filter, capped);
        }
        if (!globalQuery && !objectManager.isEventJournalEnabled(objectPath.trim())) {
            return applyFilter(fromCache, filter, capped);
        }
        if (filter == null && fromCache.size() >= capped) {
            return fromCache.subList(0, capped);
        }
        boolean skipGlobalStore = globalQuery
                && eventJournalProperties.isCassandraStore()
                && !eventJournalProperties.isCassandraGlobalTableEnabled();
        if (skipGlobalStore) {
            return applyFilter(fromCache, filter, capped);
        }
        Set<String> seen = new HashSet<>();
        for (ObjectEvent event : fromCache) {
            seen.add(event.id());
        }
        int remaining = Math.max(0, fetchLimit - fromCache.size());
        List<EventJournalRecord> records = remaining > 0
                ? eventJournalStore.queryRecent(objectPath, remaining)
                : List.of();
        List<ObjectEvent> merged = new ArrayList<>(fromCache);
        for (EventJournalRecord record : records) {
            if (seen.add(record.id())) {
                merged.add(toObjectEvent(record));
                if (merged.size() >= fetchLimit) {
                    break;
                }
            }
        }
        merged.sort((left, right) -> right.timestamp().compareTo(left.timestamp()));
        return applyFilter(merged, filter, capped);
    }

    private EventFilterDefinition resolveFilter(String filterPath) {
        if (filterPath == null || filterPath.isBlank()) {
            return null;
        }
        return eventFilterObjectService.getByPath(filterPath.trim());
    }

    private List<ObjectEvent> applyFilter(List<ObjectEvent> events, EventFilterDefinition filter, int limit) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        if (filter == null) {
            return events.size() <= limit ? events : events.subList(0, limit);
        }
        List<ObjectEvent> matched = new ArrayList<>(Math.min(events.size(), limit));
        for (ObjectEvent event : events) {
            if (eventFilterMatcher.matches(filter, event)) {
                matched.add(event);
                if (matched.size() >= limit) {
                    break;
                }
            }
        }
        return matched;
    }

    public Map<String, Object> journalStatus(String objectPath) {
        boolean masterEnabled = eventJournalProperties.isEnabled();
        boolean globalTableEnabled = eventJournalProperties.isCassandraGlobalTableEnabled();
        boolean objectEnabled = objectPath != null && !objectPath.isBlank()
                && objectManager.isEventJournalEnabled(objectPath.trim());
        boolean globalQuery = objectPath == null || objectPath.isBlank();
        boolean enabled = globalQuery ? masterEnabled : masterEnabled && objectEnabled;
        return Map.of(
                "masterEnabled", masterEnabled,
                "globalTableEnabled", globalTableEnabled,
                "objectEnabled", objectEnabled,
                "enabled", enabled
        );
    }

    private boolean shouldPersistJournal(String objectPath) {
        return eventJournalProperties.isEnabled() && objectManager.isEventJournalEnabled(objectPath);
    }

    private ObjectEvent fireInternal(
            String objectPath,
            String eventName,
            DataRecordPayloadRequest payload,
            String appId,
            AutomationMetricsRecorder.EventFireSource source,
            Instant occurredAt
    ) {
        if (appId != null && !appId.isBlank()) {
            catalogPayloadValidator.validateAtFire(appId, eventName, payload);
        }
        PlatformObject node = objectManager.require(objectPath);
        EventDescriptor descriptor = Optional.ofNullable(node.events().get(eventName))
                .orElseThrow(() -> new IllegalArgumentException("Unknown event: " + eventName));

        DataRecord resolvedPayload = DataRecordPayloadResolver.resolve(descriptor.payloadSchema(), payload);
        Instant resolvedOccurredAt = eventTimestampValidator.validateOccurredAt(occurredAt);
        ObjectEvent event = ObjectEvent.of(
                objectPath,
                eventName,
                descriptor.level(),
                resolvedPayload,
                resolvedOccurredAt
        );
        recentEventCache.append(event);
        if (shouldPersistJournal(objectPath)) {
            eventJournalAsyncWriter.enqueue(event, mapper.writeDataRecord(event.payload()));
        }
        automationMetricsRecorder.recordEventFired(source);
        publicationService.publishEventFired(objectPath, eventName);
        return event;
    }

    private ObjectEvent toObjectEvent(EventJournalRecord record) {
        DataRecord payload = mapper.readDataRecord(record.payloadJson());
        if (payload == null) {
            payload = DataRecord.empty(
                    com.ispf.core.model.DataSchema.builder("eventPayload").build()
            );
        }
        return new ObjectEvent(
                record.id(),
                record.objectPath(),
                record.eventName(),
                EventLevel.valueOf(record.level()),
                payload,
                record.occurredAt()
        );
    }

    private ObjectEvent toObjectEvent(EventHistoryEntity entity) {
        return toObjectEvent(EventJournalRecord.fromEntity(entity));
    }
}
