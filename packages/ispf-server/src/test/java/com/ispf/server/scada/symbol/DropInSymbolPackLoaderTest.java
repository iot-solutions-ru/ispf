package com.ispf.server.scada.symbol;

import com.ispf.server.config.ScadaSymbolPackProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DropInSymbolPackLoaderTest {

    @TempDir
    Path tempDir;

    private DropInSymbolPackLoader loader;

    @BeforeEach
    void setUp() {
        ScadaSymbolPackProperties properties = new ScadaSymbolPackProperties();
        properties.setSymbolPacksDir(tempDir.resolve("installed").toString());
        loader = new DropInSymbolPackLoader(properties, new ObjectMapper());
        loader.ensurePacksRoot();
    }

    @Test
    void installsDirectoryPackAndListsDetail() throws Exception {
        Path source = tempDir.resolve("source-pack");
        Files.createDirectories(source);
        Files.writeString(source.resolve("manifest.json"), """
                {
                  "version": 1,
                  "id": "demo-pack",
                  "totalSymbols": 1,
                  "categories": [{ "id": "pack-demo", "file": "demo.json", "count": 1 }]
                }
                """);
        Files.writeString(source.resolve("demo.json"), """
                [{
                  "id": "pack.demo.one",
                  "category": "pack-demo",
                  "nameEn": "One",
                  "nameRu": "Один",
                  "defaultWidth": 32,
                  "defaultHeight": 32,
                  "viewBox": "0 0 32 32",
                  "svg": "<g/>",
                  "ports": []
                }]
                """);

        Map<String, Object> installed = loader.installPackDirectory(source);
        assertThat(installed.get("packId")).isEqualTo("demo-pack");
        assertThat(loader.isPackInstalled("demo-pack")).isTrue();
        assertThat(loader.listInstalledPacks()).hasSize(1);

        Map<String, Object> detail = loader.getPackDetail("demo-pack");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) detail.get("categories");
        assertThat(categories).hasSize(1);
        assertThat(categories.getFirst().get("symbols")).asList().hasSize(1);
    }
}
