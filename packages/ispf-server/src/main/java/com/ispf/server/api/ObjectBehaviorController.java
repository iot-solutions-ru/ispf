package com.ispf.server.api;

import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.server.api.support.ObjectWriteGuard;
import com.ispf.server.object.ObjectManager;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Object behaviour descriptors — functions and events on a tree node
 * ({@code /api/v1/objects/by-path/functions|events}). Split out of {@code ObjectController};
 * URL contract unchanged.
 */
@RestController
@RequestMapping("/api/v1/objects/by-path")
public class ObjectBehaviorController {

    private final ObjectManager objectManager;
    private final ObjectWriteGuard writeGuard;

    public ObjectBehaviorController(ObjectManager objectManager, ObjectWriteGuard writeGuard) {
        this.objectManager = objectManager;
        this.writeGuard = writeGuard;
    }

    @PutMapping("/functions")
    public FunctionDescriptor upsertFunction(
            @RequestParam String path,
            @Valid @RequestBody FunctionDescriptor function,
            Authentication authentication,
            @RequestHeader HttpHeaders headers
    ) {
        path = writeGuard.canonicalPath(path, authentication);
        writeGuard.beginWrite(path, authentication, headers);
        try {
            return objectManager.upsertFunction(path, function);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            writeGuard.endWrite();
        }
    }

    @DeleteMapping("/functions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFunction(
            @RequestParam String path,
            @RequestParam String name,
            Authentication authentication,
            @RequestHeader HttpHeaders headers
    ) {
        path = writeGuard.canonicalPath(path, authentication);
        writeGuard.beginWrite(path, authentication, headers);
        try {
            objectManager.deleteFunction(path, name);
        } finally {
            writeGuard.endWrite();
        }
    }

    @PutMapping("/events")
    public EventDescriptor upsertEvent(
            @RequestParam String path,
            @Valid @RequestBody EventDescriptor event,
            Authentication authentication,
            @RequestHeader HttpHeaders headers
    ) {
        path = writeGuard.canonicalPath(path, authentication);
        writeGuard.beginWrite(path, authentication, headers);
        try {
            return objectManager.upsertEvent(path, event);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            writeGuard.endWrite();
        }
    }

    @DeleteMapping("/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(
            @RequestParam String path,
            @RequestParam String name,
            Authentication authentication,
            @RequestHeader HttpHeaders headers
    ) {
        path = writeGuard.canonicalPath(path, authentication);
        writeGuard.beginWrite(path, authentication, headers);
        try {
            objectManager.deleteEvent(path, name);
        } finally {
            writeGuard.endWrite();
        }
    }
}
