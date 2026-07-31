package com.gridveritas.core.web;

import com.gridveritas.core.domain.AuditLog;
import com.gridveritas.core.domain.Source;
import com.gridveritas.core.domain.VerificationEvent;
import com.gridveritas.core.repository.AuditLogRepository;
import com.gridveritas.core.repository.VerificationEventRepository;
import com.gridveritas.core.service.AttestationService;
import com.gridveritas.core.service.AuditService;
import com.gridveritas.core.service.MerkleService;
import com.gridveritas.core.web.dto.AttestationRequest;
import com.gridveritas.core.web.dto.AttestationResponse;
import com.gridveritas.core.web.dto.ProofResponse;
import com.gridveritas.core.web.dto.VerifyRequest;
import com.gridveritas.core.web.dto.VerifyResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AttestationController {

    private final AttestationService attestationService;
    private final MerkleService merkleService;
    private final AuditService auditService;
    private final VerificationEventRepository verificationRepo;
    private final AuditLogRepository auditRepo;

    public AttestationController(AttestationService attestationService,
                                 MerkleService merkleService,
                                 AuditService auditService,
                                 VerificationEventRepository verificationRepo,
                                 AuditLogRepository auditRepo) {
        this.attestationService = attestationService;
        this.merkleService = merkleService;
        this.auditService = auditService;
        this.verificationRepo = verificationRepo;
        this.auditRepo = auditRepo;
    }

    // ------------------------------------------------------------------ Attestations

    @PostMapping("/attestations")
    public ResponseEntity<AttestationResponse> createAttestation(
            @Valid @RequestBody AttestationRequest request) {
        AttestationResponse response = attestationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/attestations/{id}")
    public AttestationResponse getAttestation(@PathVariable UUID id) {
        return attestationService.getById(id);
    }

    @GetMapping("/attestations")
    public List<AttestationResponse> listBySource(@RequestParam UUID sourceId) {
        return attestationService.listBySource(sourceId);
    }

    @GetMapping("/attestations/{id}/proof")
    public ProofResponse getProof(@PathVariable UUID id) {
        ProofResponse proof = merkleService.buildProof(id);
        auditService.recordVerification("PROOF", id.toString(), proof.getStatus(),
                proof.isAnchored() ? "anchored=true" : "anchored=false");
        return proof;
    }

    // ------------------------------------------------------------------ Verify

    @PostMapping("/verify")
    public VerifyResponse verify(@Valid @RequestBody VerifyRequest request) {
        VerifyResponse result = attestationService.verify(request);
        auditService.recordVerification("VERIFY", request.getPayloadHash(),
                result.isValid() ? "VALID" : "INVALID_OR_NOT_FOUND", result.getMessage());
        return result;
    }

    // ------------------------------------------------------------------ Sources

    @GetMapping("/sources")
    public List<Source> listSources() {
        return attestationService.listSources();
    }

    @PostMapping("/sources")
    public ResponseEntity<Source> createSource(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "unnamed-source");
        String publicKey = body.getOrDefault("publicKey", "");
        Source created = attestationService.createSource(name, publicKey);
        auditService.recordAudit("SOURCE_CREATED", created.getId().toString(), "name=" + name);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ------------------------------------------------------------------ Audit trail

    /** Recent security/configuration events. */
    @GetMapping("/audit")
    public List<AuditLog> audit() {
        return auditRepo.findTop100ByOrderByCreatedAtDesc();
    }

    /** Recent verification events (who verified what, and the result). */
    @GetMapping("/audit/verifications")
    public List<VerificationEvent> verifications() {
        return verificationRepo.findTop100ByOrderByCreatedAtDesc();
    }
}
