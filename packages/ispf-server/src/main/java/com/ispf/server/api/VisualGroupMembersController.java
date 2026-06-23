package com.ispf.server.api;

import com.ispf.core.object.VisualGroupMember;
import com.ispf.server.api.support.ObjectCollaborationSupport;
import com.ispf.server.object.ObjectEditLeaseService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.VisualGroupService;
import com.ispf.server.security.acl.ObjectAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/objects")
public class VisualGroupMembersController {

    private final ObjectManager objectManager;
    private final ObjectAccessService objectAccessService;
    private final ObjectEditLeaseService editLeaseService;
    private final VisualGroupService visualGroupService;

    public VisualGroupMembersController(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            ObjectEditLeaseService editLeaseService,
            VisualGroupService visualGroupService
    ) {
        this.objectManager = objectManager;
        this.objectAccessService = objectAccessService;
        this.editLeaseService = editLeaseService;
        this.visualGroupService = visualGroupService;
    }

    @GetMapping("/by-path/group-members")
    public List<VisualGroupMember> listMembers(@RequestParam String path, Authentication authentication) {
        objectAccessService.requireRead(path, authentication);
        objectManager.require(path);
        return visualGroupService.listMembers(path);
    }

    @PutMapping("/by-path/group-members")
    public List<VisualGroupMember> saveMembers(
            @RequestParam String path,
            @Valid @RequestBody GroupMembersRequest request,
            Authentication authentication,
            @RequestHeader HttpHeaders headers
    ) {
        beginWrite(path, authentication, headers);
        try {
            return switch (request.action()) {
                case "set" -> visualGroupService.setMembers(path, request.members());
                case "add" -> visualGroupService.addMembers(path, request.paths());
                case "remove" -> visualGroupService.removeMembers(path, request.paths());
                case "reorder" -> visualGroupService.reorderMembers(path, request.paths());
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown action: " + request.action());
            };
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            endWrite();
        }
    }

    public record GroupMembersRequest(
            @NotBlank String action,
            List<VisualGroupMember> members,
            List<@NotBlank String> paths
    ) {
    }

    private void beginWrite(String path, Authentication authentication, HttpHeaders headers) {
        objectAccessService.requireWrite(path, authentication);
        editLeaseService.assertWritable(path, authentication != null ? authentication.getName() : "system");
        ObjectCollaborationSupport.bindWriteContext(authentication, headers);
    }

    private void endWrite() {
        ObjectCollaborationSupport.clearContext();
    }
}
