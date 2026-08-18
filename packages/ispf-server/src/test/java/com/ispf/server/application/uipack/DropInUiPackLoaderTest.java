package com.ispf.server.application.uipack;

import com.ispf.server.config.UiPackProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DropInUiPackLoaderTest {

    @TempDir
    Path tempDir;

    private DropInUiPackLoader loader;

    @BeforeEach
    void setUp() {
        UiPackProperties properties = new UiPackProperties();
        properties.setPacksDir(tempDir.resolve("installed").toString());
        loader = new DropInUiPackLoader(properties, new ObjectMapper());
        loader.ensurePacksRoot();
    }

    @Test
    void installsDirectoryAndResolvesSpaFallback() throws Exception {
        Path source = tempDir.resolve("source-pack");
        Files.createDirectories(source);
        Files.writeString(source.resolve("ui-pack.json"), """
                {
                  "id": "demo-app",
                  "appId": "demo-app",
                  "version": "1.0.0",
                  "entry": "index.html"
                }
                """);
        Files.writeString(source.resolve("index.html"), "<html><body>ok</body></html>");

        Map<String, Object> installed = loader.installPackDirectory(source, "demo-app");
        assertThat(installed.get("hostedUiUrl")).isEqualTo("/apps/demo-app/");
        assertThat(loader.isPackInstalled("demo-app")).isTrue();

        Path asset = loader.resolveAsset("demo-app", "balance");
        assertThat(asset).isNotNull();
        assertThat(Files.readString(asset)).contains("ok");
    }

    @Test
    void installsZipArchive() throws Exception {
        Path source = tempDir.resolve("zip-source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("ui-pack.json"), """
                {"appId":"zip-app","version":"1.0.0","entry":"index.html"}
                """);
        Files.writeString(source.resolve("index.html"), "<html>zip</html>");
        byte[] zip = zipDir(source);

        Map<String, Object> installed = loader.installZipArchive(zip, "zip-app");
        assertThat(installed.get("appId")).isEqualTo("zip-app");
        assertThat(loader.resolveAsset("zip-app", "")).isNotNull();
    }

    @Test
    void rejectsPathEscapeEntry() {
        assertThatThrownBy(() -> {
            Path source = tempDir.resolve("bad");
            Files.createDirectories(source);
            Files.writeString(source.resolve("ui-pack.json"), """
                    {"appId":"bad","entry":"../evil.html"}
                    """);
            Files.writeString(source.resolve("index.html"), "x");
            loader.installPackDirectory(source, "bad");
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsReservedPlatformAppId() {
        assertThatThrownBy(() -> {
            Path source = tempDir.resolve("platform-pack");
            Files.createDirectories(source);
            Files.writeString(source.resolve("ui-pack.json"), """
                    {"appId":"_platform","entry":"index.html"}
                    """);
            Files.writeString(source.resolve("index.html"), "x");
            loader.installPackDirectory(source, "_platform");
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ui-pack appId");
    }

    private static byte[] zipDir(Path source) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos);
             var walk = Files.walk(source)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                String name = source.relativize(path).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(name));
                Files.copy(path, zos);
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }
}
