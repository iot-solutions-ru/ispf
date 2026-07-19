package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.PartnerDirectoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartnerDirectoryRepository extends JpaRepository<PartnerDirectoryEntity, String> {

    List<PartnerDirectoryEntity> findAllByOrderByIdAsc();
}
