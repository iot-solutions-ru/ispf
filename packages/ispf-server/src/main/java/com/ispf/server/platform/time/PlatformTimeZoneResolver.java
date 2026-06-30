package com.ispf.server.platform.time;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves effective IANA timezone for an object path (device → parent folder → UTC).
 */
@Service
public class PlatformTimeZoneResolver {

    private final ObjectManager objectManager;

    public PlatformTimeZoneResolver(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    public String resolve(String objectPath) {
        if (objectPath == null || objectPath.isBlank()) {
            return PlatformTimeZones.DEFAULT;
        }
        String path = objectPath.trim();
        while (!path.isEmpty()) {
            Optional<String> zone = readTimeZoneVariable(path);
            if (zone.isPresent()) {
                return zone.get();
            }
            int lastDot = path.lastIndexOf('.');
            if (lastDot <= 0) {
                break;
            }
            path = path.substring(0, lastDot);
        }
        return PlatformTimeZones.DEFAULT;
    }

    private Optional<String> readTimeZoneVariable(String path) {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            return Optional.empty();
        }
        PlatformObject node = objectManager.require(path);
        Optional<Variable> variable = node.getVariable("timeZone");
        if (variable.isEmpty() || variable.get().value().isEmpty()) {
            return Optional.empty();
        }
        DataRecord record = variable.get().value().get();
        if (record.rowCount() == 0) {
            return Optional.empty();
        }
        Object raw = record.firstRow().get("value");
        if (raw == null) {
            return Optional.empty();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if (!PlatformTimeZones.isValid(text)) {
            return Optional.empty();
        }
        return Optional.of(PlatformTimeZones.normalize(text));
    }
}
