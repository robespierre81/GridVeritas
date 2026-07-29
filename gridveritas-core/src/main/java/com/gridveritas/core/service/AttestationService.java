package com.gridveritas.core.service;

import com.gridveritas.core.crypto.Ed25519Verifier;
import com.gridveritas.core.domain.Attestation;
import com.gridveritas.core.domain.Source;
import com.gridveritas.core.repository.AttestationRepository;
import com.gridveritas.core.repository.SourceRepository;
import com.gridveritas.core.web.dto.AttestationRequest;
import com.gridveritas.core.web.dto.AttestationResponse;
import com.gridveritas.core.web.dto.VerifyRequest;
import com.gridveritas.core.web.dto.VerifyResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AttestationService {

    private final AttestationRepository attestationRepository;
    private final SourceRepository sourceRepository;

    public AttestationService(AttestationRepository attestationRepository,
                              SourceRepository sourceRepository) {
        this.attestationRepository = attestationRepository;
        this.sourceRepository = sourceRepository;
    }

    /**
     * Ingest attestation and cryptographically verify the Ed25519 signature
     * against the source public key (same contract as the Go edge agent).
     */
    @Transactional
    public AttestationResponse create(AttestationRequest request) {
        Source source = sourceRepository.findById(request.getSourceId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Source not found: " + request.getSourceId()));

        boolean signatureOk = Ed25519Verifier.verify(
                source.getPublicKey(),
                request.getPayloadHash(),
                request.getSignature()
        );

        Attestation attestation = new Attestation(
                source,
                request.getPayloadHash(),
                request.getTimestamp(),
                request.getSequenceNr(),
                request.getSignature()
        );
        attestation.setSignatureValid(signatureOk);

        source.setLastSeenAt(Instant.now());
        sourceRepository.save(source);

        Attestation saved = attestationRepository.save(attestation);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AttestationResponse getById(UUID id) {
        Attestation attestation = attestationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Attestation not found: " + id));
        return toResponse(attestation);
    }

    @Transactional(readOnly = true)
    public List<AttestationResponse> listBySource(UUID sourceId) {
        return attestationRepository.findBySourceIdOrderByTimestampDesc(sourceId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Verify by payload hash:
     * 1) attestation must exist
     * 2) Ed25519 signature is re-checked against the stored source public key
     */
    @Transactional(readOnly = true)
    public VerifyResponse verify(VerifyRequest request) {
        return attestationRepository.findByPayloadHash(request.getPayloadHash())
                .map(a -> {
                    Source source = a.getSource();
                    boolean cryptoOk = Ed25519Verifier.verify(
                            source.getPublicKey(),
                            a.getPayloadHash(),
                            a.getSignature()
                    );

                    VerifyResponse resp = new VerifyResponse();
                    resp.setValid(cryptoOk);
                    resp.setAttestationId(a.getId());
                    resp.setSourceId(source.getId());
                    if (cryptoOk) {
                        resp.setMessage("Attestation found and Ed25519 signature is valid");
                    } else {
                        resp.setMessage("Attestation found but Ed25519 signature is INVALID");
                    }
                    return resp;
                })
                .orElseGet(() -> {
                    VerifyResponse resp = new VerifyResponse();
                    resp.setValid(false);
                    resp.setMessage("No attestation found for the given payload hash");
                    return resp;
                });
    }

    @Transactional(readOnly = true)
    public List<Source> listSources() {
        return sourceRepository.findAll();
    }

    @Transactional
    public Source createSource(String name, String publicKey) {
        Source source = new Source(name, publicKey);
        return sourceRepository.save(source);
    }

    private AttestationResponse toResponse(Attestation a) {
        AttestationResponse r = new AttestationResponse();
        r.setId(a.getId());
        r.setSourceId(a.getSource().getId());
        r.setSourceName(a.getSource().getName());
        r.setPayloadHash(a.getPayloadHash());
        r.setTimestamp(a.getTimestamp());
        r.setSequenceNr(a.getSequenceNr());
        r.setSignature(a.getSignature());
        r.setCreatedAt(a.getCreatedAt());
        r.setSignatureValid(a.getSignatureValid());
        if (Boolean.TRUE.equals(a.getSignatureValid())) {
            r.setStatus("SIGNATURE_VALID");
        } else if (Boolean.FALSE.equals(a.getSignatureValid())) {
            r.setStatus("SIGNATURE_INVALID");
        } else {
            r.setStatus("STORED");
        }
        return r;
    }
}
