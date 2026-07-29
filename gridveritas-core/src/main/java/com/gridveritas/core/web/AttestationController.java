package com.gridveritas.core.web;

import com.gridveritas.core.domain.Source;
import com.gridveritas.core.service.AttestationService;
import com.gridveritas.core.web.dto.AttestationRequest;
import com.gridveritas.core.web.dto.AttestationResponse;
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

    public AttestationController(AttestationService attestationService) {
        this.attestationService = attestationService;
    }

    // ------------------------------------------------------------------
    // Attestations
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Verify
    // ------------------------------------------------------------------

    @PostMapping("/verify")
    public VerifyResponse verify(@Valid @RequestBody VerifyRequest request) {
        return attestationService.verify(request);
    }

    // ------------------------------------------------------------------
    // Sources
    // ------------------------------------------------------------------

    @GetMapping("/sources")
    public List<Source> listSources() {
        return attestationService.listSources();
    }

    /**
     * Simple helper endpoint to create a source during early development.
     * In production this will be replaced by a proper registration flow.
     */
    @PostMapping("/sources")
    public ResponseEntity<Source> createSource(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "unnamed-source");
        String publicKey = body.getOrDefault("publicKey", "");
        Source created = attestationService.createSource(name, publicKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ------------------------------------------------------------------
    // Audit (minimal placeholder)
    // ------------------------------------------------------------------

    @GetMapping("/audit")
    public Map<String, String> auditPlaceholder() {
        return Map.of(
                "message", "Audit endpoint placeholder – will return verification and configuration events",
                "status", "MVP"
        );
    }
}
