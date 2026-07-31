package com.gridveritas.core.repository;

import com.gridveritas.core.domain.MerkleLeaf;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerkleLeafRepository extends JpaRepository<MerkleLeaf, UUID> {

    Optional<MerkleLeaf> findByAttestationId(UUID attestationId);

    List<MerkleLeaf> findByRootIdOrderByLeafIndexAsc(UUID rootId);
}
