package com.gridveritas.core.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A Merkle root fetched from a peer operator (M13). Append-only.
 * signatureValid / anchorValid are the local, trustless verification result.
 */
@Entity
@Table(name = "peer_roots", uniqueConstraints = {
        @UniqueConstraint(name = "uq_peer_root", columnNames = {"peer_id", "root_hash"})
})
public class PeerRoot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "peer_id", nullable = false)
    private FederationPeer peer;

    @Column(name = "operator_id", nullable = false, length = 64)
    private String operatorId;

    @Column(name = "root_hash", nullable = false, length = 64)
    private String rootHash;

    @Column(name = "prev_root_hash", length = 64)
    private String prevRootHash;

    @Column(name = "leaf_count", nullable = false)
    private int leafCount;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "operator_signature", nullable = false, length = 256)
    private String operatorSignature;

    @Column(name = "anchor_authority", length = 255)
    private String anchorAuthority;

    @Column(name = "anchor_token", columnDefinition = "bytea")
    private byte[] anchorToken;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Column(name = "anchor_valid", nullable = false)
    private boolean anchorValid;

    @Column(name = "fetched_at", nullable = false, updatable = false)
    private Instant fetchedAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public FederationPeer getPeer() {
        return peer;
    }

    public void setPeer(FederationPeer peer) {
        this.peer = peer;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
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

    public String getOperatorSignature() {
        return operatorSignature;
    }

    public void setOperatorSignature(String operatorSignature) {
        this.operatorSignature = operatorSignature;
    }

    public String getAnchorAuthority() {
        return anchorAuthority;
    }

    public void setAnchorAuthority(String anchorAuthority) {
        this.anchorAuthority = anchorAuthority;
    }

    public byte[] getAnchorToken() {
        return anchorToken;
    }

    public void setAnchorToken(byte[] anchorToken) {
        this.anchorToken = anchorToken;
    }

    public boolean isSignatureValid() {
        return signatureValid;
    }

    public void setSignatureValid(boolean signatureValid) {
        this.signatureValid = signatureValid;
    }

    public boolean isAnchorValid() {
        return anchorValid;
    }

    public void setAnchorValid(boolean anchorValid) {
        this.anchorValid = anchorValid;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
