package com.gridveritas.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * External anchor for a Merkle root: an RFC 3161 timestamp token from an
 * independent TSA. The token attests that root_hash existed no later than
 * gen_time, so recorded history cannot be altered undetected — including by the
 * platform operator. Append-only.
 */
@Entity
@Table(name = "anchors")
public class Anchor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "root_id", nullable = false)
    private MerkleRoot root;

    /** TSA URL or name that produced the token. */
    @Column(nullable = false, length = 255)
    private String authority;

    /** DER-encoded RFC 3161 TimeStampToken (CMS SignedData). */
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] token;

    /** TSA-asserted time (genTime) extracted from the token. */
    @Column(name = "gen_time")
    private Instant genTime;

    @Column(name = "serial_number", length = 128)
    private String serialNumber;

    @Column(name = "anchored_at", nullable = false, updatable = false)
    private Instant anchoredAt = Instant.now();

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

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public byte[] getToken() {
        return token;
    }

    public void setToken(byte[] token) {
        this.token = token;
    }

    public Instant getGenTime() {
        return genTime;
    }

    public void setGenTime(Instant genTime) {
        this.genTime = genTime;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public Instant getAnchoredAt() {
        return anchoredAt;
    }

    public void setAnchoredAt(Instant anchoredAt) {
        this.anchoredAt = anchoredAt;
    }
}
