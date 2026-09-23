package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.PartnerEnrollmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerEnrollmentRepository extends JpaRepository<PartnerEnrollmentEntity, String> {
}
