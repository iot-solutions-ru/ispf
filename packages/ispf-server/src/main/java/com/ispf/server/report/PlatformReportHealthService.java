package com.ispf.server.report;

import com.ispf.server.config.ReportLibreOfficeProperties;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PlatformReportHealthService {

    private final ReportLibreOfficeProperties libreOfficeProperties;

    public PlatformReportHealthService(ReportLibreOfficeProperties libreOfficeProperties) {
        this.libreOfficeProperties = libreOfficeProperties;
    }

    public ReportHealth health() {
        Optional<String> resolvedPath = LibreOfficeSupport.resolveProgramPath(libreOfficeProperties.getPath());
        String configuredPath = libreOfficeProperties.getPath() == null || libreOfficeProperties.getPath().isBlank()
                ? null
                : libreOfficeProperties.getPath().trim();
        return new ReportHealth(
                resolvedPath.isPresent(),
                configuredPath,
                resolvedPath.orElse(null),
                libreOfficeProperties.getTimeoutSeconds(),
                LibreOfficeSupport.libreOfficeRequiredMessage()
        );
    }

    public record ReportHealth(
            boolean libreOfficeAvailable,
            String configuredPath,
            String resolvedPath,
            int timeoutSeconds,
            String pdfHint
    ) {
    }
}
