package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.EventHistoryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventHistoryRepository extends JpaRepository<EventHistoryEntity, String> {

    List<EventHistoryEntity> findByObjectPathOrderByOccurredAtDesc(String objectPath, Pageable pageable);

    List<EventHistoryEntity> findAllByOrderByOccurredAtDesc(Pageable pageable);

    Optional<EventHistoryEntity> findFirstByObjectPathAndEventNameOrderByOccurredAtDesc(
            String objectPath,
            String eventName
    );
}
