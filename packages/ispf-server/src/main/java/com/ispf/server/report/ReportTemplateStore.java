package com.ispf.server.report;

import com.ispf.server.persistence.ReportTemplateRepository;
import com.ispf.server.persistence.entity.ReportTemplateEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
public class ReportTemplateStore {

    private final ReportTemplateRepository repository;

    public ReportTemplateStore(ReportTemplateRepository repository) {
        this.repository = repository;
    }

    public boolean exists(String reportPath) {
        return repository.existsById(reportPath);
    }

    public Optional<StoredTemplate> find(String reportPath) {
        return repository.findById(reportPath).map(this::toStored);
    }

    @Transactional
    public void save(String reportPath, String format, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Template content is required");
        }
        validateFormat(format);
        ReportTemplateEntity entity = repository.findById(reportPath).orElseGet(ReportTemplateEntity::new);
        entity.setReportPath(reportPath);
        entity.setFormat(format.trim().toLowerCase());
        entity.setContent(content);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    @Transactional
    public void delete(String reportPath) {
        repository.deleteById(reportPath);
    }

    static void validateFormat(String format) {
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("Template format is required");
        }
        String normalized = format.trim().toLowerCase();
        if (!normalized.equals("xlsx")
                && !normalized.equals("xls")
                && !normalized.equals("docx")
                && !normalized.equals("doc")
                && !normalized.equals("html")) {
            throw new IllegalArgumentException("Unsupported template format: " + format);
        }
    }

    private StoredTemplate toStored(ReportTemplateEntity entity) {
        return new StoredTemplate(entity.getReportPath(), entity.getFormat(), entity.getContent(), entity.getUpdatedAt());
    }

    public record StoredTemplate(String reportPath, String format, byte[] content, Instant updatedAt) {
    }
}
