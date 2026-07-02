package com.ispf.server.platform;

import com.ispf.server.platform.haystack.HaystackFilterParser;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BL-102: Haystack filter query with tenant scope and path ACL.
 */
@Service
public class HaystackQueryService {

    private final HaystackExportService haystackExportService;
    private final ObjectAccessService objectAccessService;
    private final TenantScopeService tenantScopeService;

    public HaystackQueryService(
            HaystackExportService haystackExportService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        this.haystackExportService = haystackExportService;
        this.objectAccessService = objectAccessService;
        this.tenantScopeService = tenantScopeService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> query(
            Authentication authentication,
            String filter,
            String rootPath,
            String entityKind,
            int offset,
            int limit
    ) {
        List<String> requiredMarkers = HaystackFilterParser.parseRequiredMarkers(filter);
        int scanLimit = Math.max(1, Math.min(offset + limit, HaystackExportService.SEARCH_MAX_LIMIT));
        Map<String, Object> raw = haystackExportService.searchByTags(
                rootPath,
                requiredMarkers,
                entityKind,
                scanLimit
        );

        List<Map<String, Object>> rawMatches = castMatches(raw.get("matches"));
        List<Map<String, Object>> visible = new ArrayList<>();
        for (Map<String, Object> match : rawMatches) {
            String objectPath = String.valueOf(match.getOrDefault("objectPath", ""));
            if (objectPath.isBlank()) {
                continue;
            }
            if (authentication != null) {
                if (!tenantScopeService.isPathVisible(objectPath, authentication)) {
                    continue;
                }
                if (!objectAccessService.canRead(objectPath, authentication)) {
                    continue;
                }
            }
            visible.add(enrichQueryMatch(match));
        }

        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(limit > 0 ? limit : HaystackExportService.SEARCH_DEFAULT_LIMIT,
                HaystackExportService.SEARCH_MAX_LIMIT));
        int fromIndex = Math.min(safeOffset, visible.size());
        int toIndex = Math.min(fromIndex + safeLimit, visible.size());
        List<Map<String, Object>> page = visible.subList(fromIndex, toIndex);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("formatVersion", raw.get("formatVersion"));
        payload.put("exportedAt", raw.get("exportedAt"));
        payload.put("filter", HaystackFilterParser.toFilterString(requiredMarkers));
        payload.put("rootPath", raw.get("rootPath"));
        payload.put("entityKind", raw.get("entityKind"));
        payload.put("offset", safeOffset);
        payload.put("limit", safeLimit);
        payload.put("count", page.size());
        payload.put("totalVisible", visible.size());
        payload.put("matches", page);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castMatches(Object raw) {
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> matches = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    matches.add((Map<String, Object>) map);
                }
            }
            return matches;
        }
        return List.of();
    }

    private static Map<String, Object> enrichQueryMatch(Map<String, Object> match) {
        Map<String, Object> enriched = new LinkedHashMap<>(match);
        Object objectPath = match.get("objectPath");
        if (objectPath != null) {
            enriched.put("path", objectPath);
        }
        return enriched;
    }
}
