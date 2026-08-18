package com.gridveritas.core.web;

import com.gridveritas.core.domain.Attestation;
import com.gridveritas.core.service.AttestationService;
import com.gridveritas.core.service.SettlementService;
import com.gridveritas.core.web.dto.AttestationResponse;
import com.gridveritas.core.web.dto.SettlementDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class SettlementController {

    private final SettlementService settlementService;
    private final AttestationService attestationService;

    public SettlementController(SettlementService settlementService,
                                AttestationService attestationService) {
        this.settlementService = settlementService;
        this.attestationService = attestationService;
    }

    @GetMapping("/settlements/mapping")
    public Map<String, Object> mapping() {
        return settlementService.mapping();
    }

    @GetMapping("/aggregators")
    public List<SettlementDtos.AggregatorView> aggregators() {
        return settlementService.listAggregators();
    }

    @PostMapping("/aggregators")
    public ResponseEntity<SettlementDtos.AggregatorView> createAggregator(
            @Valid @RequestBody SettlementDtos.AggregatorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(settlementService.createAggregator(request));
    }

    @GetMapping("/resources")
    public List<SettlementDtos.ResourceView> resources() {
        return settlementService.listResources();
    }

    @PostMapping("/resources")
    public ResponseEntity<SettlementDtos.ResourceView> createResource(
            @Valid @RequestBody SettlementDtos.ResourceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(settlementService.createResource(request));
    }

    @GetMapping("/resources/{id}")
    public SettlementDtos.ResourceView resource(@PathVariable UUID id) {
        return settlementService.getResource(id);
    }

    @GetMapping("/resources/{id}/attestations")
    public List<AttestationResponse> resourceAttestations(@PathVariable UUID id) {
        return settlementService.resourceAttestations(id).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/settlements")
    public List<SettlementDtos.SettlementView> settlements() {
        return settlementService.listSettlements();
    }

    @PostMapping("/settlements")
    public ResponseEntity<SettlementDtos.SettlementView> createSettlement(
            @Valid @RequestBody SettlementDtos.SettlementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(settlementService.createSettlement(request));
    }

    @GetMapping("/settlements/{id}")
    public SettlementDtos.SettlementView settlement(@PathVariable UUID id) {
        return settlementService.getSettlement(id);
    }

    private AttestationResponse toResponse(Attestation a) {
        return attestationService.getById(a.getId());
    }
}
