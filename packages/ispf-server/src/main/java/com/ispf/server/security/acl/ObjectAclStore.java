package com.ispf.server.security.acl;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class ObjectAclStore {

    private static final String CACHE_NAME = "objectAcl";

    private final JdbcTemplate jdbcTemplate;
    private final CacheManager cacheManager;

    public ObjectAclStore(JdbcTemplate jdbcTemplate, CacheManager cacheManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.cacheManager = cacheManager;
    }

    public List<ObjectAclEntry> listByPath(String objectPath) {
        return jdbcTemplate.query("""
                SELECT id, object_path, principal_type, principal_id, permission, created_at
                FROM object_acl_entries
                WHERE object_path = ?
                ORDER BY principal_type, principal_id, permission
                """,
                (rs, rowNum) -> new ObjectAclEntry(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("object_path"),
                        rs.getString("principal_type"),
                        rs.getString("principal_id"),
                        rs.getString("permission"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                objectPath
        );
    }

    public boolean hasEntriesForPathOrAncestor(String objectPath) {
        String current = objectPath;
        while (current != null && !current.isBlank()) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM object_acl_entries WHERE object_path = ?",
                    Integer.class,
                    current
            );
            if (count != null && count > 0) {
                return true;
            }
            int dot = current.lastIndexOf('.');
            current = dot == -1 ? null : current.substring(0, dot);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public List<ObjectAclEntry> findEffectiveEntries(String objectPath) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            return loadEffectiveEntries(objectPath);
        }
        List<ObjectAclEntry> cached = cache.get(objectPath, List.class);
        if (cached != null) {
            return cached;
        }
        List<ObjectAclEntry> loaded = loadEffectiveEntries(objectPath);
        cache.put(objectPath, loaded);
        return loaded;
    }

    private List<ObjectAclEntry> loadEffectiveEntries(String objectPath) {
        String current = objectPath;
        while (current != null && !current.isBlank()) {
            List<ObjectAclEntry> entries = listByPath(current);
            if (!entries.isEmpty()) {
                return entries;
            }
            int dot = current.lastIndexOf('.');
            current = dot == -1 ? null : current.substring(0, dot);
        }
        return List.of();
    }

    public void invalidateEffectiveEntriesCache(String objectPath) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            return;
        }
        if (objectPath == null || objectPath.isBlank()) {
            cache.clear();
            return;
        }
        String current = objectPath;
        while (current != null && !current.isBlank()) {
            cache.evict(current);
            int dot = current.lastIndexOf('.');
            current = dot == -1 ? null : current.substring(0, dot);
        }
    }

    public void replaceEntries(String objectPath, List<ObjectAclEntryDraft> drafts) {
        jdbcTemplate.update("DELETE FROM object_acl_entries WHERE object_path = ?", objectPath);
        invalidateEffectiveEntriesCache(objectPath);
        Instant now = Instant.now();
        for (ObjectAclEntryDraft draft : drafts) {
            jdbcTemplate.update("""
                    INSERT INTO object_acl_entries (
                        id, object_path, principal_type, principal_id, permission, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    objectPath,
                    draft.principalType(),
                    draft.principalId(),
                    draft.permission(),
                    Timestamp.from(now)
            );
        }
    }

    public record ObjectAclEntryDraft(String principalType, String principalId, String permission) {
    }

    public record ObjectAclEntry(
            UUID id,
            String objectPath,
            String principalType,
            String principalId,
            String permission,
            Instant createdAt
    ) {
    }
}
