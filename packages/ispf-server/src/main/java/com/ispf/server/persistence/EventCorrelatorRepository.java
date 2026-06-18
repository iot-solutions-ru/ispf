package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.EventCorrelatorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventCorrelatorRepository extends JpaRepository<EventCorrelatorEntity, String> {

    List<EventCorrelatorEntity> findByEventNameAndEnabledTrue(String eventName);

    long count();
}
