package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.ReportTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportTemplateRepository extends JpaRepository<ReportTemplateEntity, String> {
}
