package com.gridveritas.core.web.dto;

import java.util.UUID;

public class VerifyResponse {

    private boolean valid;
    private String message;
    private UUID attestationId;
    private UUID sourceId;

    public VerifyResponse() {
    }

    public VerifyResponse(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public UUID getAttestationId() {
        return attestationId;
    }

    public void setAttestationId(UUID attestationId) {
        this.attestationId = attestationId;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }
}
