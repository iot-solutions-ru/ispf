package com.ispf.server.object;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.object.VisualGroupConstants;
import com.ispf.core.object.VisualGroupMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class VisualGroupService {

    private static final DataSchema MEMBERS_SCHEMA = DataSchema.builder("groupMembersJson")
            .field("value", FieldType.STRING)
            .build();

    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;

    public VisualGroupService(ObjectManager objectManager, ObjectMapper objectMapper) {
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
    }

    public boolean isVisualGroup(String path) {
        return objectManager.tree().findByPath(path)
                .map(node -> node.type() == ObjectType.VISUAL_GROUP)
                .orElse(false);
    }

    public void requireVisualGroup(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.VISUAL_GROUP) {
            throw new IllegalArgumentException("Not a visual group: " + path);
        }
    }

    public List<VisualGroupMember> listMembers(String groupPath) {
        requireVisualGroup(groupPath);
        return readMembers(objectManager.require(groupPath));
    }

    @Transactional
    public List<VisualGroupMember> setMembers(String groupPath, List<VisualGroupMember> members) {
        requireVisualGroup(groupPath);
        List<VisualGroupMember> normalized = normalizeMembers(groupPath, members);
        writeMembers(groupPath, normalized);
        return normalized;
    }

    @Transactional
    public List<VisualGroupMember> addMembers(String groupPath, List<String> paths) {
        requireVisualGroup(groupPath);
        List<VisualGroupMember> current = new ArrayList<>(listMembers(groupPath));
        Set<String> existing = new HashSet<>();
        for (VisualGroupMember member : current) {
            existing.add(member.path());
        }
        int nextOrder = current.stream().mapToInt(VisualGroupMember::sortOrder).max().orElse(-1) + 1;
        for (String path : paths) {
            String trimmed = path != null ? path.trim() : "";
            if (trimmed.isBlank() || existing.contains(trimmed)) {
                continue;
            }
            assertMemberExists(groupPath, trimmed);
            current.add(new VisualGroupMember(trimmed, nextOrder++));
            existing.add(trimmed);
        }
        return setMembers(groupPath, current);
    }

    @Transactional
    public List<VisualGroupMember> removeMembers(String groupPath, List<String> paths) {
        requireVisualGroup(groupPath);
        Set<String> toRemove = new HashSet<>();
        for (String path : paths) {
            if (path != null && !path.isBlank()) {
                toRemove.add(path.trim());
            }
        }
        List<VisualGroupMember> remaining = listMembers(groupPath).stream()
                .filter(member -> !toRemove.contains(member.path()))
                .toList();
        return setMembers(groupPath, remaining);
    }

    @Transactional
    public List<VisualGroupMember> reorderMembers(String groupPath, List<String> orderedPaths) {
        requireVisualGroup(groupPath);
        Map<String, VisualGroupMember> byPath = new LinkedHashMap<>();
        for (VisualGroupMember member : listMembers(groupPath)) {
            byPath.put(member.path(), member);
        }
        List<VisualGroupMember> reordered = new ArrayList<>();
        int order = 0;
        for (String path : orderedPaths) {
            String trimmed = path != null ? path.trim() : "";
            if (trimmed.isBlank() || !byPath.containsKey(trimmed)) {
                continue;
            }
            reordered.add(new VisualGroupMember(trimmed, order++));
            byPath.remove(trimmed);
        }
        for (VisualGroupMember leftover : byPath.values()) {
            reordered.add(new VisualGroupMember(leftover.path(), order++));
        }
        return setMembers(groupPath, reordered);
    }

    @Transactional
    public void removePathFromAllGroups(String deletedPath) {
        for (PlatformObject node : objectManager.tree().all()) {
            if (node.type() != ObjectType.VISUAL_GROUP) {
                continue;
            }
            List<VisualGroupMember> members = readMembers(node);
            boolean changed = members.removeIf(member -> member.path().equals(deletedPath));
            if (changed) {
                writeMembers(node.path(), members);
            }
        }
    }

    private List<VisualGroupMember> normalizeMembers(String groupPath, List<VisualGroupMember> members) {
        List<VisualGroupMember> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int order = 0;
        for (VisualGroupMember member : members) {
            if (member == null || member.path() == null || member.path().isBlank()) {
                continue;
            }
            String path = member.path().trim();
            if (seen.contains(path)) {
                continue;
            }
            assertMemberExists(groupPath, path);
            assertNoCycle(groupPath, path, new HashSet<>());
            normalized.add(new VisualGroupMember(path, order++));
            seen.add(path);
        }
        normalized.sort(Comparator.comparingInt(VisualGroupMember::sortOrder));
        for (int i = 0; i < normalized.size(); i++) {
            VisualGroupMember member = normalized.get(i);
            normalized.set(i, new VisualGroupMember(member.path(), i));
        }
        return normalized;
    }

    private void assertMemberExists(String groupPath, String memberPath) {
        if (memberPath.equals(groupPath)) {
            throw new IllegalArgumentException("Visual group cannot reference itself: " + groupPath);
        }
        objectManager.tree().findByPath(memberPath)
                .orElseThrow(() -> new IllegalArgumentException("Unknown member path: " + memberPath));
    }

    private void assertNoCycle(String groupPath, String memberPath, Set<String> visiting) {
        if (!isVisualGroup(memberPath)) {
            return;
        }
        if (!visiting.add(memberPath)) {
            throw new IllegalArgumentException("Cycle detected in visual group nesting");
        }
        for (VisualGroupMember nested : readMembers(objectManager.require(memberPath))) {
            if (nested.path().equals(groupPath)) {
                throw new IllegalArgumentException("Cycle detected: group " + groupPath + " <-> " + memberPath);
            }
            assertNoCycle(groupPath, nested.path(), visiting);
        }
        visiting.remove(memberPath);
    }

    private List<VisualGroupMember> readMembers(PlatformObject group) {
        return group.getVariable(VisualGroupConstants.MEMBERS_VARIABLE)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(json -> !json.isBlank())
                .map(this::parseMembers)
                .orElse(List.of());
    }

    private List<VisualGroupMember> parseMembers(String json) {
        try {
            List<MemberDto> dtos = objectMapper.readValue(json, new TypeReference<>() {});
            return dtos.stream()
                    .map(dto -> new VisualGroupMember(dto.path(), dto.sortOrder()))
                    .sorted(Comparator.comparingInt(VisualGroupMember::sortOrder))
                    .toList();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid group members JSON: " + e.getMessage());
        }
    }

    private void writeMembers(String groupPath, List<VisualGroupMember> members) {
        try {
            List<MemberDto> dtos = members.stream().map(MemberDto::from).toList();
            String json = objectMapper.writeValueAsString(dtos);
            DataRecord record = DataRecord.single(MEMBERS_SCHEMA, Map.of("value", json));
            objectManager.upsertSystemVariable(
                    groupPath,
                    VisualGroupConstants.MEMBERS_VARIABLE,
                    MEMBERS_SCHEMA,
                    record
            );
            objectManager.tree().require(groupPath);
            objectManager.publishStructureChange(groupPath);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist group members", e);
        }
    }

    private record MemberDto(String path, int sortOrder) {
        static MemberDto from(VisualGroupMember member) {
            return new MemberDto(member.path(), member.sortOrder());
        }
    }
}
