package com.gridveritas.core.service;

import com.gridveritas.core.crypto.Ed25519Verifier;
import com.gridveritas.core.crypto.MerkleTree;
import com.gridveritas.core.crypto.TsaVerifier;
import com.gridveritas.core.domain.Attestation;
import com.gridveritas.core.domain.MerkleLeaf;
import com.gridveritas.core.domain.MerkleRoot;
import com.gridveritas.core.repository.AnchorRepository;
import com.gridveritas.core.repository.AttestationRepository;
import com.gridveritas.core.repository.MerkleLeafRepository;
import com.gridveritas.core.repository.MerkleRootRepository;
import com.gridveritas.core.web.dto.ProofResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds Merkle provenance: a scheduled job periodically seals all not-yet-sealed
 * attestation leaves into a new, chained Merkle root; buildProof produces a
 * verifiable inclusion proof, including the external anchor (M6) when present.
 */
@Service
public class MerkleService {

    private static final Logger log = LoggerFactory.getLogger(MerkleService.class);

    private final AttestationRepository attestationRepository;
    private final MerkleRootRepository merkleRootRepository;
    private final MerkleLeafRepository merkleLeafRepository;
    private final AnchorRepository anchorRepository;
    private final TsaVerifier tsaVerifier;

    @Value("${gridveritas.merkle.max-batch:1000}")
    private int maxBatch;

    public MerkleService(AttestationRepository attestationRepository,
                         MerkleRootRepository merkleRootRepository,
                         MerkleLeafRepository merkleLeafRepository,
                         AnchorRepository anchorRepository,
                         TsaVerifier tsaVerifier) {
        this.attestationRepository = attestationRepository;
        this.merkleRootRepository = merkleRootRepository;
        this.merkleLeafRepository = merkleLeafRepository;
        this.anchorRepository = anchorRepository;
        this.tsaVerifier = tsaVerifier;
    }

    /**
     * Seal all currently unsealed leaves into one new root. Runs on a fixed delay
     * (default 60s; override with gridveritas.merkle.seal-interval-ms).
     */
    @Scheduled(fixedDelayString = "${gridveritas.merkle.seal-interval-ms:60000}")
    @Transactional
    public void sealNewLeaves() {
        List<Attestation> batch =
                attestationRepository.findUnsealed(PageRequest.of(0, maxBatch));
        if (batch.isEmpty()) {
            return;
        }

        List<byte[]> leaves = new ArrayList<>(batch.size());
        for (Attestation a : batch) {
            leaves.add(HexFormat.of().parseHex(a.getLeafHash()));
        }
        byte[] rootHash = MerkleTree.merkleRoot(leaves);

        String prev = merkleRootRepository.findTopByOrderByComputedAtDesc()
                .map(MerkleRoot::getRootHash)
                .orElse(null);

        MerkleRoot root = new MerkleRoot();
        root.setRootHash(HexFormat.of().formatHex(rootHash));
        root.setPrevRootHash(prev);
        root.setLeafCount(batch.size());
        root.setComputedAt(Instant.now());
        MerkleRoot savedRoot = merkleRootRepository.save(root);

        for (int i = 0; i < batch.size(); i++) {
            Attestation a = batch.get(i);
            MerkleLeaf ml = new MerkleLeaf();
            ml.setRoot(savedRoot);
            ml.setLeafIndex(i);
            ml.setAttestation(a);
            ml.setLeafHash(a.getLeafHash());
            merkleLeafRepository.save(ml);
        }

        log.info("Sealed Merkle root {} over {} leaves (prev={})",
                savedRoot.getRootHash(), batch.size(), prev);
    }

    /** Verifiable inclusion proof for one attestation, with anchor when available. */
    @Transactional(readOnly = true)
    public ProofResponse buildProof(UUID attestationId) {
        Attestation att = attestationRepository.findById(attestationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Attestation not found: " + attestationId));

        ProofResponse resp = new ProofResponse();
        resp.setAttestationId(attestationId);
        resp.setLeafHash(att.getLeafHash());

        Optional<MerkleLeaf> leafOpt = merkleLeafRepository.findByAttestationId(attestationId);
        if (leafOpt.isEmpty()) {
            resp.setStatus("PENDING_SEAL");
            resp.setMessage("Attestation not yet included in a Merkle root; the sealing job has not run for it yet.");
            resp.setAnchored(false);
            return resp;
        }

        MerkleLeaf leaf = leafOpt.get();
        MerkleRoot root = leaf.getRoot();

        // The leaf that was sealed and externally anchored:
        resp.setLeafHash(leaf.getLeafHash());
        // Recompute the leaf from the record's CURRENT stored fields and compare.
        // A mismatch means the stored record was altered after anchoring (tamper detected).
        try {
            byte[] msg = Ed25519Verifier.canonicalAttestation(
                    att.getSource().getId().toString(),
                    att.getSequenceNr(),
                    att.getTimestamp().toEpochMilli(),
                    HexFormat.of().parseHex(att.getPayloadHash()));
            String recomputed = HexFormat.of().formatHex(MerkleTree.leafHash(msg));
            resp.setCurrentLeaf(recomputed);
            resp.setProvenanceIntact(recomputed.equalsIgnoreCase(leaf.getLeafHash()));
        } catch (Exception e) {
            resp.setProvenanceIntact(false);
        }

        List<MerkleLeaf> ordered =
                merkleLeafRepository.findByRootIdOrderByLeafIndexAsc(root.getId());
        List<byte[]> leaves = new ArrayList<>(ordered.size());
        for (MerkleLeaf l : ordered) {
            leaves.add(HexFormat.of().parseHex(l.getLeafHash()));
        }

        List<MerkleTree.PathStep> path = MerkleTree.auditPath(leaves, leaf.getLeafIndex());
        List<ProofResponse.Step> steps = new ArrayList<>(path.size());
        for (MerkleTree.PathStep p : path) {
            steps.add(new ProofResponse.Step(
                    HexFormat.of().formatHex(p.hash()),
                    p.siblingOnRight() ? "RIGHT" : "LEFT"));
        }

        resp.setStatus("SEALED");
        resp.setLeafIndex(leaf.getLeafIndex());
        resp.setAuditPath(steps);
        resp.setRootHash(root.getRootHash());
        resp.setPrevRootHash(root.getPrevRootHash());
        resp.setLeafCount(root.getLeafCount());
        resp.setComputedAt(root.getComputedAt());

        Optional<com.gridveritas.core.domain.Anchor> anchorOpt =
                anchorRepository.findFirstByRootIdOrderByAnchoredAtAsc(root.getId());
        if (anchorOpt.isPresent()) {
            var anchor = anchorOpt.get();
            resp.setAnchored(true);
            resp.setAnchorAuthority(anchor.getAuthority());
            resp.setAnchorTime(anchor.getGenTime());
            resp.setAnchorSerial(anchor.getSerialNumber());
            resp.setAnchorToken(Base64.getEncoder().encodeToString(anchor.getToken()));

            byte[] rootDigest = HexFormat.of().parseHex(root.getRootHash());
            TsaVerifier.Result vr = tsaVerifier.verify(anchor.getToken(), rootDigest);
            resp.setAnchorSignatureValid(vr.valid());
            resp.setAnchorTrusted(vr.trusted());
            resp.setMessage("Included in a Merkle root and externally anchored (RFC 3161); "
                    + "anchor " + (vr.valid() ? "signature verified" : "verification FAILED")
                    + (vr.trusted() ? " and trust-pinned." : "."));
        } else {
            resp.setAnchored(false);
            resp.setMessage("Included in a Merkle root. External anchor pending (will be added by the anchoring job).");
        }
        if (Boolean.FALSE.equals(resp.getProvenanceIntact())) {
            resp.setMessage("PROVENANCE MISMATCH: the stored record no longer matches its "
                    + "externally anchored leaf. Tampering detected.");
        }
        return resp;
    }
}
