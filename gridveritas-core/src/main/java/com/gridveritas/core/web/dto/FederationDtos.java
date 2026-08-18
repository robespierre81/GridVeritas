package com.gridveritas.core.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Request/response types for M13 federation. */
public final class FederationDtos {

    private FederationDtos() {
    }

    public static class OperatorInfo {
        public UUID operatorId;
        public String publicKey;
        public String algorithm = "Ed25519";
        public String domainTag = "GridVeritas-Federation-Root-v1";
    }

    public static class AnchorView {
        public String authority;
        public String token;
        public Instant genTime;
        public String serial;
    }

    public static class PublishedRoot {
        public String rootHash;
        public String prevRootHash;
        public int leafCount;
        public Instant computedAt;
        public String signature;
        public AnchorView anchor;
    }

    public static class PublishedBundle {
        public UUID operatorId;
        public String publicKey;
        public String algorithm = "Ed25519";
        public List<PublishedRoot> roots = new ArrayList<>();
    }

    public static class RegisterPeerRequest {
        @NotBlank
        public String name;
        @NotBlank
        public String baseUrl;
        @NotBlank
        public String publicKey;
        public Boolean enabled;
    }

    public static class PeerView {
        public UUID id;
        public String name;
        public String baseUrl;
        public String publicKey;
        public boolean enabled;
        public Instant lastFetchedAt;
        public String lastError;
        public Instant createdAt;
    }

    public static class PeerRootView {
        public UUID id;
        public UUID peerId;
        public String peerName;
        public String operatorId;
        public String rootHash;
        public String prevRootHash;
        public int leafCount;
        public Instant computedAt;
        public boolean signatureValid;
        public boolean anchorValid;
        public String anchorAuthority;
        public Instant fetchedAt;
    }

    public static class VerifyRequest {
        @NotBlank
        public String operatorId;
        @NotBlank
        public String publicKey;
        @NotBlank
        public String rootHash;
        public String prevRootHash;
        public int leafCount;
        public Instant computedAt;
        @NotBlank
        public String signature;
        public String anchorToken;
    }

    public static class VerifyResult {
        public boolean signatureValid;
        public boolean anchorPresent;
        public boolean anchorValid;
        public String detail;
    }

    public static class FetchReport {
        public UUID peerId;
        public int seen;
        public int stored;
        public int alreadyKnown;
        public int signatureRejected;
        public String error;
    }
}
