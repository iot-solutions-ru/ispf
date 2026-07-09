package com.ispf.server.history;

import com.ispf.server.config.HistorianColdArchiveProperties;
import com.ispf.server.config.HistorianTierProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes cold-tier Parquet files to a local directory tree ({@code localRoot/bucket/prefix/...}).
 * Ops may mount S3 or sync this tree to object storage.
 */
@Component
class FilesystemColdArchiveSink implements ColdArchiveSink {

    private final HistorianTierProperties tierProperties;
    private final HistorianColdArchiveProperties archiveProperties;
    private final Path root;

    FilesystemColdArchiveSink(
            HistorianTierProperties tierProperties,
            HistorianColdArchiveProperties archiveProperties
    ) {
        this.tierProperties = tierProperties;
        this.archiveProperties = archiveProperties;
        String configuredRoot = archiveProperties.localRoot();
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            this.root = Path.of(configuredRoot).toAbsolutePath().normalize();
        } else {
            String dataDir = System.getenv("ISPF_DATA_DIR");
            if (dataDir == null || dataDir.isBlank()) {
                dataDir = "./data";
            }
            this.root = Path.of(dataDir, "historian-cold-archive").toAbsolutePath().normalize();
        }
    }

    @Override
    public boolean isConfigured() {
        var cold = tierProperties.coldTier().getCold();
        return cold.getBucket() != null && !cold.getBucket().isBlank();
    }

    @Override
    public void put(String objectKey, byte[] content) {
        if (!isConfigured()) {
            throw new IllegalStateException("Cold archive bucket is not configured");
        }
        var cold = tierProperties.coldTier().getCold();
        String prefix = cold.getPrefix() != null ? cold.getPrefix() : "";
        Path target = root.resolve(cold.getBucket()).resolve(prefix).resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid cold archive object key: " + objectKey);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write cold archive object: " + target, ex);
        }
    }

    Path rootPath() {
        return root;
    }
}
