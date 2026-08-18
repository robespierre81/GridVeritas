package com.gridveritas.core.web;

import com.gridveritas.core.service.FederationService;
import com.gridveritas.core.web.dto.FederationDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/federation")
public class FederationController {

    private final FederationService federationService;

    public FederationController(FederationService federationService) {
        this.federationService = federationService;
    }

    @GetMapping("/info")
    public FederationDtos.OperatorInfo info() {
        return federationService.info();
    }

    @GetMapping("/roots")
    public FederationDtos.PublishedBundle roots(@RequestParam(required = false) Integer limit) {
        return federationService.publish(limit);
    }

    @PostMapping("/verify")
    public FederationDtos.VerifyResult verify(@Valid @RequestBody FederationDtos.VerifyRequest request) {
        return federationService.verify(request);
    }

    @GetMapping("/peers")
    public List<FederationDtos.PeerView> peers() {
        return federationService.listPeers();
    }

    @PostMapping("/peers")
    public ResponseEntity<FederationDtos.PeerView> addPeer(
            @Valid @RequestBody FederationDtos.RegisterPeerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(federationService.addPeer(request));
    }

    @PostMapping("/peers/{id}/fetch")
    public FederationDtos.FetchReport fetch(@PathVariable UUID id) {
        return federationService.fetchPeer(id);
    }

    @GetMapping("/peer-roots")
    public List<FederationDtos.PeerRootView> peerRoots(
            @RequestParam(required = false, defaultValue = "50") int limit) {
        return federationService.listPeerRoots(limit);
    }
}
