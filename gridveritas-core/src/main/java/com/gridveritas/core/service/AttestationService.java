package com.gridveritas.core.service;

import com.gridveritas.core.crypto.Ed25519Verifier;
import com.gridveritas.core.crypto.MerkleTree;
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
import java.util.HexFormat;
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
     * Ingest an attestation:
     * - reject a duplicate (source, sequence) as a replay (409),
     * - verify the Ed25519 signature over the canonical message
     *   (payload hash + source id + sequence + timestamp),
     * - store the Merkle leaf hash = SHA-256(0x00 || canonical message).
     *
     * The DB constraint uq_attestation_source_seq is the hard backstop; this
     * pre-check gives a clean 409 on the normal (single-writer) path.
     */
    @Transactional
    public AttestationResponse create(AttestationRequest request) {
        Source source = sourceRepository.findById(request.getSourceId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Source not found: " + request.getSourceId()));

        if (attestationRepository.existsBySourceIdAndSequenceNr(source.getId(), request.getSequenceNr())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Duplicate attestation: sequence " + request.getSequenceNr()
                            + " already exists for this source (replay rejected)");
        }

        byte[] message = canonicalMessage(
                source.getId().toString(),
                request.getSequenceNr(),
                request.getTimestampEpochMillis(),
                request.getPayloadHash()
        );
        boolean signatureOk = message != null
                && Ed25519Verifier.verify(source.getPublicKey(), message, request.getSignature());

        Attestation attestation = new Attestation(
                source,
                request.getPayloadHash(),
                Instant.ofEpochMilli(request.getTimestampEpochMillis()),
                request.getSequenceNr(),
                request.getSignature()
        );
        attestation.setSignatureValid(signatureOk);
        if (message != null) {
            attestation.setLeafHash(HexFormat.of().formatHex(MerkleTree.leafHash(message)));
        }

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
     * 1) an attestation for the hash must exist
     * 2) the Ed25519 signature is re-checked over the canonical message rebuilt from
     *    the STORED metadata, so any post-hoc change to timestamp/sequence/source breaks it.
     *
     * This covers source authenticity and metadata integrity. Detecting deletion or
     * reordering of whole records is provided by the Merkle proof (GET
     * /attestations/{id}/proof) and, once available, the external anchor (M6).
     */
    @Transactional(readOnly = true)
    public VerifyResponse verify(VerifyRequest request) {
        return attestationRepository.findByPayloadHash(request.getPayloadHash())
                .map(a -> {
                    Source source = a.getSource();
                    long tsMillis = a.getTimestamp() != null ? a.getTimestamp().toEpochMilli() : 0L;
                    byte[] message = canonicalMessage(
                            source.getId().toString(),
                            a.getSequenceNr(),
                            tsMillis,
                            a.getPayloadHash()
                    );
                    boolean cryptoOk = message != null
                            && Ed25519Verifier.verify(source.getPublicKey(), message, a.getSignature());

                    VerifyResponse resp = new VerifyResponse();
                    resp.setValid(cryptoOk);
                    resp.setAttestationId(a.getId());
                    resp.setSourceId(source.getId());
                    resp.setMessage(cryptoOk
                            ? "Attestation found and Ed25519 signature is valid (payload + metadata)"
                            : "Attestation found but Ed25519 signature is INVALID");
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

    /**
     * Rebuilds the canonical signed message. Returns null if the sequence is missing
     * or the payload hash is not valid hex, in which case the signature is treated as
     * invalid (and no leaf hash is stored) rather than throwing.
     */
    private static byte[] canonicalMessage(String sourceId, Long sequence,
                                           long tsEpochMillis, String payloadHashHex) {
        if (sequence == null || payloadHashHex == null) {
            return null;
        }
        try {
            byte[] payloadHashRaw = HexFormat.of().parseHex(payloadHashHex.trim());
            return Ed25519Verifier.canonicalAttestation(sourceId, sequence, tsEpochMillis, payloadHashRaw);
        } catch (IllegalArgumentException e) {
            return null;
        }
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