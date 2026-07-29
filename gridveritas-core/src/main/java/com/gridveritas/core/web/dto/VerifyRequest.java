package com.gridveritas.core.web.dto;

import jakarta.validation.constraints.NotBlank;

public class VerifyRequest {

    @NotBlank
    private String payloadHash;

    // optional: sourceId or attestationId for more targeted checks later

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }
}
