package com.ispf.server.api;

import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.security.acl.ObjectAclStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/objects/by-path/acl")
public class ObjectAclController {

    private final ObjectAccessService objectAccessService;

    public ObjectAclController(ObjectAccessService objectAccessService) {
        this.objectAccessService = objectAccessService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam String path) {
        List<Map<String, Object>> entries = objectAccessService.listEntries(path).stream()
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("principalType", entry.principalType());
                    row.put("principalId", entry.principalId());
                    row.put("permission", entry.permission());
                    return row;
                })
                .toList();
        return Map.of("objectPath", path, "entries", entries);
    }

    @PutMapping
    public Map<String, Object> replace(@RequestParam String path, @RequestBody ReplaceAclRequest request) {
        List<ObjectAclStore.ObjectAclEntryDraft> drafts = request.entries().stream()
                .map(item -> new ObjectAclStore.ObjectAclEntryDraft(
                        item.principalType(),
                        item.principalId(),
                        item.permission()
                ))
                .toList();
        objectAccessService.replaceEntries(path, drafts);
        return Map.of("objectPath", path, "status", "updated", "count", drafts.size());
    }

    public record ReplaceAclRequest(List<AclEntryDto> entries) {
    }

    public record AclEntryDto(String principalType, String principalId, String permission) {
    }
}
