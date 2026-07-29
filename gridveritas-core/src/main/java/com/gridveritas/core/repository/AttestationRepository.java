package com.gridveritas.core.repository;

import com.gridveritas.core.domain.Attestation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttestationRepository extends JpaRepository<Attestation, UUID> {

    List<Attestation> findBySourceIdOrderByTimestampDesc(UUID sourceId);

    Optional<Attestation> findByPayloadHash(String payloadHash);
}
