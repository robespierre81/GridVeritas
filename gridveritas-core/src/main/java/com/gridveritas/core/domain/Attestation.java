package com.gridveritas.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attestations")
public class Attestation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(name = "payload_hash", nullable = false, length = 128)
    private String payloadHash;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "sequence_nr")
    private Long sequenceNr;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String signature;

    /** true when Ed25519 verification against the source public key succeeded */
    @Column(name = "signature_valid")
    private Boolean signatureValid;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // --- constructors ---

    public Attestation() {
    }

    public Attestation(Source source, String payloadHash, Instant timestamp, Long sequenceNr, String signature) {
        this.source = source;
        this.payloadHash = payloadHash;
        this.timestamp = timestamp;
        this.sequenceNr = sequenceNr;
        this.signature = signature;
    }

    // --- getters / setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Long getSequenceNr() {
        return sequenceNr;
    }

    public void setSequenceNr(Long sequenceNr) {
        this.sequenceNr = sequenceNr;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public Boolean getSignatureValid() {
        return signatureValid;
    }

    public void setSignatureValid(Boolean signatureValid) {
        this.signatureValid = signatureValid;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
