package com.ispf.server.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

final class LibreOfficeSupport {

    private static final Logger log = LoggerFactory.getLogger(LibreOfficeSupport.class);

    private LibreOfficeSupport() {
    }

    static Optional<String> resolveProgramPath(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path path = Path.of(configuredPath.trim());
            if (hasSoffice(path)) {
                return Optional.of(path.toString());
            }
            log.warn("Configured LibreOffice path has no soffice binary: {}", path);
            return Optional.empty();
        }
        for (String candidate : List.of(
                "/usr/lib/libreoffice/program",
                "/usr/lib64/libreoffice/program",
                "C:/Program Files/LibreOffice/program",
                "C:/Program Files (x86)/LibreOffice/program")) {
            Path path = Path.of(candidate);
            if (hasSoffice(path)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    static boolean isLibreOfficeRequiredError(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("libre/open office")
                || lower.contains("libreoffice")
                || lower.contains("openoffice");
    }

    static String libreOfficeRequiredMessage() {
        return "PDF export from spreadsheet templates requires LibreOffice on the server "
                + "(ispf.reports.libre-office.path or install libreoffice-nogui). "
                + "Use XLSX or HTML for exports without LibreOffice.";
    }

    private static boolean hasSoffice(Path path) {
        return Files.isRegularFile(path.resolve("soffice"))
                || Files.isRegularFile(path.resolve("soffice.bin"))
                || Files.isRegularFile(path.resolve("soffice.exe"));
    }
}
