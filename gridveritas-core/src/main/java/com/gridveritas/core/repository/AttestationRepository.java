package com.gridveritas.core.repository;

import com.gridveritas.core.domain.Attestation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttestationRepository extends JpaRepository<Attestation, UUID> {

    List<Attestation> findBySourceIdOrderByTimestampDesc(UUID sourceId);

    List<Attestation> findBySourceIdInOrderByTimestampDesc(Collection<UUID> sourceIds);

    List<Attestation> findBySourceIdInAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(
            Collection<UUID> sourceIds, Instant periodStart, Instant periodEnd);

    Optional<Attestation> findByPayloadHash(String payloadHash);

    /** True if this (source, sequence) pair already exists — i.e. a replay. */
    boolean existsBySourceIdAndSequenceNr(UUID sourceId, Long sequenceNr);

    /** Recent attestations for off-critical-path anomaly detection, grouped-friendly order. */
    List<Attestation> findByCreatedAtAfterOrderBySourceIdAscSequenceNrAsc(Instant since);

    /**
     * Attestations not yet part of any Merkle root, in a deterministic order
     * (createdAt, then id as a stable tiebreaker). This ordering is frozen into
     * merkle_leaves.leaf_index when the batch is sealed.
     */
    @Query("select a from Attestation a "
            + "where a.leafHash is not null "
            + "and a.id not in (select l.attestation.id from MerkleLeaf l) "
            + "order by a.createdAt asc, a.id asc")
    List<Attestation> findUnsealed(Pageable pageable);
}
