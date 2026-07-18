package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.PlatformCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformCredentialRepository extends JpaRepository<PlatformCredentialEntity, String> {

    Optional<PlatformCredentialEntity> findByObjectPath(String objectPath);
}
