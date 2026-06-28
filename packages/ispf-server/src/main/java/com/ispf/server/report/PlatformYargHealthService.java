package com.ispf.server.report;

import com.ispf.server.config.ReportYargProperties;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PlatformYargHealthService {

    private final ReportYargProperties reportYargProperties;

    public PlatformYargHealthService(ReportYargProperties reportYargProperties) {
        this.reportYargProperties = reportYargProperties;
    }

    public YargHealth health() {
        ReportYargProperties.LibreOffice config = reportYargProperties.getLibreOffice();
        Optional<String> resolvedPath = YargReportingSupport.resolveProgramPath(config.getPath());
        String configuredPath = config.getPath() == null || config.getPath().isBlank() ? null : config.getPath().trim();
        return new YargHealth(
                resolvedPath.isPresent(),
                configuredPath,
                resolvedPath.orElse(null),
                config.getTimeoutSeconds(),
                config.getPorts(),
                YargReportingSupport.libreOfficeRequiredMessage()
        );
    }

    public record YargHealth(
            boolean libreOfficeAvailable,
            String configuredPath,
            String resolvedPath,
            int timeoutSeconds,
            java.util.List<Integer> ports,
            String pdfHint
    ) {
    }
}
