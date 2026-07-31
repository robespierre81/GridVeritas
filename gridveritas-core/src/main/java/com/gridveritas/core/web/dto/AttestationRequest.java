package com.gridveritas.core.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class AttestationRequest {

    @NotNull
    private UUID sourceId;

    @NotBlank
    private String payloadHash;

    /**
     * Source-claimed timestamp as epoch milliseconds (UTC).
     * This is the value that is covered by the signature, so it is transmitted
     * as an integer to avoid any string/format ambiguity between clients.
     */
    @NotNull
    private Long timestampEpochMillis;

    /** Per-source monotonic sequence. Now covered by the signature, hence required. */
    @NotNull
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

    public Long getTimestampEpochMillis() {
        return timestampEpochMillis;
    }

    public void setTimestampEpochMillis(Long timestampEpochMillis) {
        this.timestampEpochMillis = timestampEpochMillis;
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
