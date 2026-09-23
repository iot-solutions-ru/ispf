package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.AuditEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String> {

    List<AuditEventEntity> findAllByOrderByOccurredAtDesc(Pageable pageable);

    List<AuditEventEntity> findByCategoryOrderByOccurredAtDesc(String category, Pageable pageable);
}
