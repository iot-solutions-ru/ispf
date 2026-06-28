package com.ispf.server.function.java;

import com.ispf.core.function.ObjectJavaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Builds a {@code javac} classpath that includes {@code ispf-core} API types.
 * When running {@code java -jar ispf-server.jar}, {@code java.class.path} is only the outer boot jar.
 */
final class JavaFunctionCompileClasspath {

    private static final Logger log = LoggerFactory.getLogger(JavaFunctionCompileClasspath.class);

    private static final Path CACHE_DIR = Path.of(
            System.getProperty("java.io.tmpdir"),
            "ispf-java-function-cp"
    );

    private static volatile String cachedClasspath;

    private JavaFunctionCompileClasspath() {
    }

    static String get() {
        String classpath = cachedClasspath;
        if (classpath != null) {
            return classpath;
        }
        synchronized (JavaFunctionCompileClasspath.class) {
            if (cachedClasspath != null) {
                return cachedClasspath;
            }
            cachedClasspath = build();
            log.info(
                    "Java function compile classpath ready ({} entries, ispf-core present={})",
                    cachedClasspath.split(java.io.File.pathSeparator).length,
                    cachedClasspath.contains("ispf-core")
            );
            return cachedClasspath;
        }
    }

    private static String build() {
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        collectFromBootJar(entries);
        for (ClassLoader loader = ObjectJavaFunction.class.getClassLoader(); loader != null; loader = loader.getParent()) {
            collectFromLoader(loader, entries);
        }
        String system = System.getProperty("java.class.path");
        if (system != null && !system.isBlank()) {
            for (String part : system.split(java.io.File.pathSeparator)) {
                if (!part.isBlank()) {
                    entries.add(part);
                }
            }
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("Java function compile classpath is empty");
        }
        if (entries.stream().noneMatch(entry -> entry.contains("ispf-core"))) {
            throw new IllegalStateException(
                    "Java function compile classpath missing ispf-core (entries=" + entries.size() + ")"
            );
        }
        return String.join(java.io.File.pathSeparator, entries);
    }

    private static void collectFromBootJar(Set<String> entries) {
        String system = System.getProperty("java.class.path");
        if (system == null || system.isBlank()) {
            return;
        }
        for (String part : system.split(java.io.File.pathSeparator)) {
            if (part.isBlank() || !part.endsWith(".jar")) {
                continue;
            }
            Path bootJar = Paths.get(part);
            if (!Files.isRegularFile(bootJar)) {
                continue;
            }
            try (JarFile jar = new JarFile(bootJar.toFile())) {
                boolean bootLayout = false;
                for (JarEntry entry : jar.stream().toList()) {
                    String name = entry.getName();
                    if (!name.startsWith("BOOT-INF/lib/") || !name.endsWith(".jar")) {
                        continue;
                    }
                    bootLayout = true;
                    entries.add(extractFromOuterJar(bootJar, name).toString());
                }
                if (!bootLayout) {
                    entries.add(bootJar.toString());
                }
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to read boot jar " + bootJar, ex);
            }
        }
    }

    private static void collectFromLoader(ClassLoader loader, Set<String> entries) {
        URL[] urls = loaderUrls(loader);
        if (urls == null) {
            return;
        }
        for (URL url : urls) {
            String entry = toClasspathEntry(url);
            if (entry != null) {
                entries.add(entry);
            }
        }
    }

    private static URL[] loaderUrls(ClassLoader loader) {
        if (loader instanceof java.net.URLClassLoader urlLoader) {
            return urlLoader.getURLs();
        }
        try {
            var method = loader.getClass().getMethod("getURLs");
            Object value = method.invoke(loader);
            if (value instanceof URL[] urls) {
                return urls;
            }
        } catch (ReflectiveOperationException ignored) {
            // not a URL-based loader
        }
        return null;
    }

    private static String toClasspathEntry(URL url) {
        try {
            String external = url.toExternalForm();
            if (external.startsWith("jar:nested:")) {
                return extractNestedJar(external).toString();
            }
            if (external.startsWith("jar:file:")) {
                return resolveJarFileUrl(external);
            }
            if ("file".equals(url.getProtocol())) {
                Path path = Paths.get(url.toURI());
                return Files.isDirectory(path) || external.endsWith(".jar") ? path.toString() : null;
            }
        } catch (Exception ex) {
            return null;
        }
        return null;
    }

    private static String resolveJarFileUrl(String external) throws IOException {
        String spec = external.substring("jar:file:".length());
        int sep = spec.indexOf("!/");
        if (sep < 0) {
            return Paths.get(URI.create("file:" + spec)).toString();
        }
        Path outerJar = Paths.get(URI.create("file:" + spec.substring(0, sep)));
        String entryPath = spec.substring(sep + 2);
        if (entryPath.endsWith("/")) {
            entryPath = entryPath.substring(0, entryPath.length() - 1);
        }
        return extractFromOuterJar(outerJar, entryPath).toString();
    }

    private static Path extractNestedJar(String nestedUrl) throws IOException {
        String remainder = nestedUrl.substring("jar:nested:".length());
        int outerEnd = remainder.indexOf("/!/");
        if (outerEnd < 0) {
            throw new IOException("Invalid nested jar URL: " + nestedUrl);
        }
        Path outerJar = Paths.get(remainder.substring(0, outerEnd));
        String entryPath = remainder.substring(outerEnd + 3);
        if (entryPath.endsWith("!/")) {
            entryPath = entryPath.substring(0, entryPath.length() - 2);
        } else if (entryPath.endsWith("!")) {
            entryPath = entryPath.substring(0, entryPath.length() - 1);
        }
        return extractFromOuterJar(outerJar, entryPath);
    }

    private static Path extractFromOuterJar(Path outerJar, String entryPath) throws IOException {
        Files.createDirectories(CACHE_DIR);
        String cacheName = Integer.toHexString((outerJar.toString() + "!" + entryPath).hashCode())
                + "-"
                + Path.of(entryPath).getFileName();
        Path cached = CACHE_DIR.resolve(cacheName);
        if (Files.exists(cached) && Files.size(cached) > 0) {
            return cached;
        }
        Path tmp = CACHE_DIR.resolve(cacheName + ".part");
        try (JarFile jar = new JarFile(outerJar.toFile())) {
            JarEntry entry = jar.getJarEntry(entryPath);
            if (entry == null) {
                throw new IOException("Missing nested entry " + entryPath + " in " + outerJar);
            }
            try (InputStream in = jar.getInputStream(entry)) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        try {
            Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            if (!Files.exists(cached)) {
                throw ex;
            }
        }
        return cached;
    }

    static void clearCacheForTests() {
        cachedClasspath = null;
    }
}
