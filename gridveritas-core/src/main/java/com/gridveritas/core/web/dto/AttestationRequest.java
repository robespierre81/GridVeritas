package com.gridveritas.core.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public class AttestationRequest {

    @NotNull
    private UUID sourceId;

    @NotBlank
    private String payloadHash;

    @NotNull
    private Instant timestamp;

    private Long sequenceNr;

    @NotBlank
    private String signature;

    // getters / setters

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
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
}
