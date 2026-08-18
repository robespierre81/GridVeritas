package com.gridveritas.core.repository;

import com.gridveritas.core.domain.MerkleRoot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerkleRootRepository extends JpaRepository<MerkleRoot, UUID> {

    Optional<MerkleRoot> findTopByOrderByComputedAtDesc();

    List<MerkleRoot> findAllByOrderByComputedAtDesc(Pageable pageable);

    /** Roots that do not yet have an external anchor, oldest first. */
    @Query("select r from MerkleRoot r "
            + "where r.id not in (select a.root.id from Anchor a) "
            + "order by r.computedAt asc")
    List<MerkleRoot> findWithoutAnchor(Pageable pageable);
}
