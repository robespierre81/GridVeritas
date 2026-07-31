package com.gridveritas.core.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Immutable membership record: which attestation is leaf #leafIndex of which root.
 * Written once when a root is sealed; keeps the attestations table append-only and
 * freezes the leaf ordering used for proofs.
 */
@Entity
@Table(
        name = "merkle_leaves",
        uniqueConstraints = @UniqueConstraint(name = "uq_merkle_leaf_att", columnNames = "att_id")
)
public class MerkleLeaf {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "root_id", nullable = false)
    private MerkleRoot root;

    @Column(name = "leaf_index", nullable = false)
    private int leafIndex;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "att_id", nullable = false)
    private Attestation attestation;

    @Column(name = "leaf_hash", nullable = false, length = 64)
    private String leafHash;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public MerkleRoot getRoot() {
        return root;
    }

    public void setRoot(MerkleRoot root) {
        this.root = root;
    }

    public int getLeafIndex() {
        return leafIndex;
    }

    public void setLeafIndex(int leafIndex) {
        this.leafIndex = leafIndex;
    }

    public Attestation getAttestation() {
        return attestation;
    }

    public void setAttestation(Attestation attestation) {
        this.attestation = attestation;
    }

    public String getLeafHash() {
        return leafHash;
    }

    public void setLeafHash(String leafHash) {
        this.leafHash = leafHash;
    }
}
