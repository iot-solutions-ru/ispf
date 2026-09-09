package com.ispf.driver.snmp.mib;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Global MIB library for the SNMP driver — shared by all SNMP devices.
 */
public final class SnmpMibLibrary {

    private static final SnmpMibLibrary INSTANCE = new SnmpMibLibrary();

    /** Soft cap against oversized uploads filling the shared MIB directory. */
    public static final int MAX_MIB_BYTES = 2 * 1024 * 1024;

    private volatile Path root = resolveDefaultRoot();
    private final Object lock = new Object();
    private final Map<String, SnmpMibModule> modulesByFile = new ConcurrentHashMap<>();

    private SnmpMibLibrary() {
    }

    public static SnmpMibLibrary get() {
        return INSTANCE;
    }

    /** Override storage root (tests / server packs-dir). */
    public void setRoot(Path root) {
        Objects.requireNonNull(root, "root");
        synchronized (lock) {
            this.root = root;
            modulesByFile.clear();
            reloadAllUnlocked();
        }
    }

    public Path root() {
        return root;
    }

    public void ensureLoaded() {
        synchronized (lock) {
            Path configured = resolveDefaultRoot();
            if (!configured.equals(root) && modulesByFile.isEmpty()) {
                this.root = configured;
            }
            if (modulesByFile.isEmpty() && Files.isDirectory(root)) {
                reloadAllUnlocked();
            }
        }
    }

    private static Path resolveDefaultRoot() {
        String explicit = firstNonBlank(
                System.getProperty("ispf.snmp.mibs-dir"),
                System.getenv("ISPF_SNMP_MIBS_DIR")
        );
        if (explicit != null) {
            return Path.of(explicit);
        }
        String packs = firstNonBlank(
                System.getProperty("ispf.driver.packs-dir"),
                System.getenv("ISPF_DRIVER_PACKS_DIR"),
                "./data/drivers"
        );
        return Path.of(packs, "snmp", "mibs");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public List<SnmpMibModule.FileInfo> listFiles() throws IOException {
        ensureRoot();
        ensureLoaded();
        synchronized (lock) {
            List<SnmpMibModule.FileInfo> out = new ArrayList<>();
            for (Map.Entry<String, SnmpMibModule> e : modulesByFile.entrySet()) {
                Path file = root.resolve(e.getKey());
                long size = Files.exists(file) ? Files.size(file) : 0L;
                out.add(new SnmpMibModule.FileInfo(
                        e.getKey(),
                        e.getValue().moduleName(),
                        size,
                        e.getValue().parseErrors().isEmpty() ? "ok" : "error"
                ));
            }
            out.sort(Comparator.comparing(SnmpMibModule.FileInfo::fileName, String.CASE_INSENSITIVE_ORDER));
            return out;
        }
    }

    public SnmpMibModule.FileInfo importFile(String fileName, byte[] content) throws IOException {
        ensureRoot();
        String safe = sanitizeFileName(fileName);
        if (content == null || content.length == 0) {
            throw new IOException("MIB content is empty");
        }
        if (content.length > MAX_MIB_BYTES) {
            throw new IOException("MIB content exceeds " + MAX_MIB_BYTES + " bytes");
        }
        String text = new String(content, StandardCharsets.UTF_8);
        SnmpMibModule parsed = SnmpMibParser.parse(safe, text);
        Path target = root.resolve(safe);
        Files.writeString(target, text, StandardCharsets.UTF_8);
        synchronized (lock) {
            modulesByFile.put(safe, parsed);
            reResolveAllUnlocked();
        }
        return new SnmpMibModule.FileInfo(
                safe,
                parsed.moduleName(),
                content.length,
                parsed.parseErrors().isEmpty() ? "ok" : "error"
        );
    }

    public void deleteFile(String fileName) throws IOException {
        String safe = sanitizeFileName(fileName);
        Path target = root.resolve(safe);
        Files.deleteIfExists(target);
        synchronized (lock) {
            modulesByFile.remove(safe);
            reResolveAllUnlocked();
        }
    }

    public Map<String, SnmpMibObject> objects() {
        ensureLoaded();
        synchronized (lock) {
            Map<String, SnmpMibObject> all = new LinkedHashMap<>();
            for (SnmpMibModule module : modulesByFile.values()) {
                for (SnmpMibObject object : module.objects().values()) {
                    all.putIfAbsent(object.qualifiedName(), object);
                    all.putIfAbsent(object.name(), object);
                }
            }
            return Map.copyOf(all);
        }
    }

    public List<SnmpMibObject> listObjects() {
        ensureLoaded();
        synchronized (lock) {
            return modulesByFile.values().stream()
                    .flatMap(m -> m.objects().values().stream())
                    .sorted(Comparator.comparing(SnmpMibObject::qualifiedName))
                    .toList();
        }
    }

    private void ensureRoot() throws IOException {
        Files.createDirectories(root);
    }

    private void reloadAllUnlocked() {
        modulesByFile.clear();
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.list(root)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".mib") || n.endsWith(".txt") || n.endsWith(".my");
                    })
                    .sorted()
                    .toList();
            for (Path file : files) {
                try {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    modulesByFile.put(file.getFileName().toString(), SnmpMibParser.parse(file.getFileName().toString(), text));
                } catch (Exception ignored) {
                    // keep other modules
                }
            }
        } catch (IOException ignored) {
            return;
        }
        reResolveAllUnlocked();
    }

    private void reResolveAllUnlocked() {
        Map<String, String> oidByName = new LinkedHashMap<>();
        for (SnmpMibModule module : modulesByFile.values()) {
            oidByName.putAll(module.oidAssignments());
        }
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<String, String> e : new ArrayList<>(oidByName.entrySet())) {
                String resolved = resolveOidExpression(e.getValue(), oidByName);
                if (resolved != null && !resolved.equals(e.getValue()) && isNumericOid(resolved)) {
                    oidByName.put(e.getKey(), resolved);
                    changed = true;
                }
            }
        } while (changed);

        for (SnmpMibModule module : modulesByFile.values()) {
            module.applyResolvedOids(oidByName);
        }
    }

    static String resolveOidExpression(String expression, Map<String, String> oidByName) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        String trimmed = expression.trim();
        if (isNumericOid(trimmed)) {
            return trimmed;
        }
        // parent childSubId  OR  parent
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0) {
            return null;
        }
        String parent = parts[0];
        String parentOid = oidByName.get(parent);
        if (parentOid == null || !isNumericOid(parentOid)) {
            return trimmed;
        }
        StringBuilder out = new StringBuilder(parentOid);
        for (int i = 1; i < parts.length; i++) {
            out.append('.').append(parts[i]);
        }
        return out.toString();
    }

    static boolean isNumericOid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c == '.' || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return value.chars().anyMatch(Character::isDigit);
    }

    static String sanitizeFileName(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            throw new IOException("MIB file name is required");
        }
        String base = Path.of(fileName).getFileName().toString().trim();
        if (base.isBlank() || base.contains("..")) {
            throw new IOException("Invalid MIB file name");
        }
        String lower = base.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".mib") || lower.endsWith(".txt") || lower.endsWith(".my"))) {
            base = base + ".mib";
        }
        return base;
    }

    /** Test helper. */
    void clearForTests() {
        synchronized (lock) {
            modulesByFile.clear();
        }
    }
}
