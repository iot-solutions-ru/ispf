package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.AlarmShelfRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlarmShelfRequestRepository extends JpaRepository<AlarmShelfRequestEntity, String> {

    List<AlarmShelfRequestEntity> findAllByOrderByRequestedAtDesc();
}
