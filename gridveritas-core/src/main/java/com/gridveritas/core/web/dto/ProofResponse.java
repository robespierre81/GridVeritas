package com.gridveritas.core.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Verifiable inclusion proof for one attestation. An independent verifier folds
 * auditPath into leafHash (MerkleTree.rootFromAuditPath) and checks it equals
 * rootHash; when anchored, the RFC 3161 token proves rootHash existed by anchorTime.
 */
public class ProofResponse {

    public static class Step {
        private String hash;      // hex sibling hash
        private String position;  // "LEFT" or "RIGHT" (side the sibling sits on)

        public Step() {
        }

        public Step(String hash, String position) {
            this.hash = hash;
            this.position = position;
        }

        public String getHash() {
            return hash;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }

        public String getPosition() {
            return position;
        }

        public void setPosition(String position) {
            this.position = position;
        }
    }

    private UUID attestationId;
    private String status;          // SEALED | PENDING_SEAL
    private String message;
    private String leafHash;        // hex, the frozen leaf that was sealed & anchored
    private String currentLeaf;     // hex, recomputed from the record's CURRENT stored fields
    private Boolean provenanceIntact; // true if currentLeaf still matches the anchored leaf
    private Integer leafIndex;
    private List<Step> auditPath;
    private String rootHash;
    private String prevRootHash;
    private Integer leafCount;
    private Instant computedAt;

    // Anchoring (M6)
    private boolean anchored;
    private String anchorAuthority;
    private Instant anchorTime;      // TSA-asserted genTime
    private String anchorSerial;
    private String anchorToken;      // base64 DER RFC 3161 token
    private Boolean anchorSignatureValid; // token signature + imprint + EKU verified
    private Boolean anchorTrusted;        // signer chains to the pinned TSA CA (null/false if not pinned)

    // getters / setters

    public UUID getAttestationId() {
        return attestationId;
    }

    public void setAttestationId(UUID attestationId) {
        this.attestationId = attestationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLeafHash() {
        return leafHash;
    }

    public void setLeafHash(String leafHash) {
        this.leafHash = leafHash;
    }

    public Integer getLeafIndex() {
        return leafIndex;
    }

    public void setLeafIndex(Integer leafIndex) {
        this.leafIndex = leafIndex;
    }

    public List<Step> getAuditPath() {
        return auditPath;
    }

    public void setAuditPath(List<Step> auditPath) {
        this.auditPath = auditPath;
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

    public Integer getLeafCount() {
        return leafCount;
    }

    public void setLeafCount(Integer leafCount) {
        this.leafCount = leafCount;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }

    public boolean isAnchored() {
        return anchored;
    }

    public void setAnchored(boolean anchored) {
        this.anchored = anchored;
    }

    public String getAnchorAuthority() {
        return anchorAuthority;
    }

    public void setAnchorAuthority(String anchorAuthority) {
        this.anchorAuthority = anchorAuthority;
    }

    public Instant getAnchorTime() {
        return anchorTime;
    }

    public void setAnchorTime(Instant anchorTime) {
        this.anchorTime = anchorTime;
    }

    public String getAnchorSerial() {
        return anchorSerial;
    }

    public void setAnchorSerial(String anchorSerial) {
        this.anchorSerial = anchorSerial;
    }

    public String getAnchorToken() {
        return anchorToken;
    }

    public void setAnchorToken(String anchorToken) {
        this.anchorToken = anchorToken;
    }

    public Boolean getAnchorSignatureValid() {
        return anchorSignatureValid;
    }

    public void setAnchorSignatureValid(Boolean anchorSignatureValid) {
        this.anchorSignatureValid = anchorSignatureValid;
    }

    public Boolean getAnchorTrusted() {
        return anchorTrusted;
    }

    public void setAnchorTrusted(Boolean anchorTrusted) {
        this.anchorTrusted = anchorTrusted;
    }

    public String getCurrentLeaf() {
        return currentLeaf;
    }

    public void setCurrentLeaf(String currentLeaf) {
        this.currentLeaf = currentLeaf;
    }

    public Boolean getProvenanceIntact() {
        return provenanceIntact;
    }

    public void setProvenanceIntact(Boolean provenanceIntact) {
        this.provenanceIntact = provenanceIntact;
    }
}
