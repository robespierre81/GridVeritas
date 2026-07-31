package com.gridveritas.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A sealed Merkle root over a batch of attestation leaves. Roots are chained via
 * prevRootHash so that whole periods cannot be silently dropped. Anchoring of the
 * root to an external RFC 3161 TSA is milestone M6 (not represented here yet).
 */
@Entity
@Table(name = "merkle_roots")
public class MerkleRoot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "root_hash", nullable = false, length = 64)
    private String rootHash;              // hex SHA-256

    @Column(name = "prev_root_hash", length = 64)
    private String prevRootHash;          // hex SHA-256 of the previous root, null for the first

    @Column(name = "leaf_count", nullable = false)
    private int leafCount;

    @Column(name = "computed_at", nullable = false, updatable = false)
    private Instant computedAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRootHash() {
        return rootHash;
    }

    public void setRootHash(String rootHash) {
        this.rootHash = rootHash;
    }

    public String getPrevRootHash() {
        return prevRootHash;
    }

    public void setPrevRootHash(String prevRootHash) {
        this.prevRootHash = prevRootHash;
    }

    public int getLeafCount() {
        return leafCount;
    }

    public void setLeafCount(int leafCount) {
        this.leafCount = leafCount;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }
}
