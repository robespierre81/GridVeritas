package com.gridveritas.core.service;

import com.gridveritas.core.crypto.MerkleTree;
import com.gridveritas.core.crypto.TsaVerifier;
import com.gridveritas.core.domain.Anchor;
import com.gridveritas.core.domain.Attestation;
import com.gridveritas.core.domain.MerkleRoot;
import com.gridveritas.core.domain.Source;
import com.gridveritas.core.repository.AnchorRepository;
import com.gridveritas.core.repository.AttestationRepository;
import com.gridveritas.core.repository.MerkleLeafRepository;
import com.gridveritas.core.repository.MerkleRootRepository;
import com.gridveritas.core.repository.SourceRepository;
import com.gridveritas.core.web.dto.AttestationRequest;
import com.gridveritas.core.web.dto.AttestationResponse;
import com.gridveritas.core.web.dto.ProofResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MerkleService is where individual attestations become tamper-evident history:
 * sealNewLeaves() batches unsealed attestations into a hash-chained Merkle root,
 * and buildProof() both returns a verifiable inclusion proof AND independently
 * recomputes the leaf from the record's current stored fields to catch a
 * post-hoc edit (the "provenance intact" check). Signatures don't need to be
 * cryptographically valid here - AttestationService computes leafHash from the
 * canonical message regardless of signature validity, and that's the only
 * dependency this service has on ingest.
 */
@DataJpaTest
@ActiveProfiles("test")
class MerkleServiceTest {

    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private AttestationRepository attestationRepository;
    @Autowired
    private MerkleRootRepository merkleRootRepository;
    @Autowired
    private MerkleLeafRepository merkleLeafRepository;
    @Autowired
    private AnchorRepository anchorRepository;

    private AttestationService attestationService;
    private MerkleService merkleService;

    private AttestationService attestationService() {
        if (attestationService == null) {
            attestationService = new AttestationService(attestationRepository, sourceRepository);
        }
        return attestationService;
    }

    private MerkleService merkleService() {
        if (merkleService == null) {
            merkleService = new MerkleService(attestationRepository, merkleRootRepository,
                    merkleLeafRepository, anchorRepository, new TsaVerifier(""));
            ReflectionTestUtils.setField(merkleService, "maxBatch", 1000);
        }
        return merkleService;
    }

    private static byte[] payloadHash(String seed) {
        byte[] out = new byte[32];
        byte[] src = seed.getBytes();
        for (int i = 0; i < out.length; i++) {
            out[i] = src[i % src.length];
        }
        return out;
    }

    private UUID createAttestation(UUID sourceId, long sequenceNr, String seed) {
        AttestationRequest req = new AttestationRequest();
        req.setSourceId(sourceId);
        req.setSequenceNr(sequenceNr);
        req.setTimestampEpochMillis(1_700_000_000_000L + sequenceNr * 1000);
        req.setPayloadHash(HexFormat.of().formatHex(payloadHash(seed)));
        req.setSignature("not-a-real-signature"); // MerkleService only needs leafHash, not signature validity
        AttestationResponse resp = attestationService().create(req);
        return resp.getId();
    }

    @Test
    void sealNewLeavesDoesNothingWhenThereIsNothingUnsealed() {
        merkleService().sealNewLeaves();

        assertThat(merkleRootRepository.findAll()).isEmpty();
    }

    @Test
    void sealNewLeavesSealsAllUnsealedAttestationsIntoOneRoot() {
        Source source = sourceRepository.save(new Source("agent-1", "irrelevant-key"));
        createAttestation(source.getId(), 1, "a");
        createAttestation(source.getId(), 2, "b");
        createAttestation(source.getId(), 3, "c");

        merkleService().sealNewLeaves();

        List<MerkleRoot> roots = merkleRootRepository.findAll();
        assertThat(roots).hasSize(1);
        MerkleRoot root = roots.get(0);
        assertThat(root.getLeafCount()).isEqualTo(3);
        assertThat(root.getPrevRootHash()).isNull(); // first-ever root has no predecessor
        assertThat(merkleLeafRepository.findByRootIdOrderByLeafIndexAsc(root.getId())).hasSize(3);
    }

    @Test
    void secondSealChainsToThePreviousRootHash() {
        Source source = sourceRepository.save(new Source("agent-1", "irrelevant-key"));
        createAttestation(source.getId(), 1, "a");
        merkleService().sealNewLeaves();
        MerkleRoot firstRoot = merkleRootRepository.findTopByOrderByComputedAtDesc().orElseThrow();

        createAttestation(source.getId(), 2, "b");
        merkleService().sealNewLeaves();
        MerkleRoot secondRoot = merkleRootRepository.findTopByOrderByComputedAtDesc().orElseThrow();

        assertThat(secondRoot.getId()).isNotEqualTo(firstRoot.getId());
        assertThat(secondRoot.getPrevRootHash()).isEqualTo(firstRoot.getRootHash());
    }

    @Test
    void buildProofReportsPendingSealBeforeTheSealingJobHasRun() {
        Source source = sourceRepository.save(new Source("agent-1", "irrelevant-key"));
        UUID attestationId = createAttestation(source.getId(), 1, "a");

        ProofResponse proof = merkleService().buildProof(attestationId);

        assertThat(proof.getStatus()).isEqualTo("PENDING_SEAL");
        assertThat(proof.isAnchored()).isFalse();
    }

    @Test
    void buildProofReturnsAnInclusionProofThatIndependentlyReconstructsTheRootHash() {
        Source source = sourceRepository.save(new Source("agent-1", "irrelevant-key"));
        UUID a1 = createAttestation(source.getId(), 1, "a");
        createAttestation(source.getId(), 2, "b");
        createAttestation(source.getId(), 3, "c");
        merkleService().sealNewLeaves();

        ProofResponse proof = merkleService().buildProof(a1);

        assertThat(proof.getStatus()).isEqualTo("SEALED");
        assertThat(proof.getProvenanceIntact()).isTrue();
        assertThat(proof.isAnchored()).isFalse();

        // An independent verifier recomputes the root from just the leaf + audit
        // path - this is the actual guarantee buildProof is supposed to provide.
        byte[] leaf = HexFormat.of().parseHex(proof.getLeafHash());
        List<MerkleTree.PathStep> path = proof.getAuditPath().stream()
                .map(s -> new MerkleTree.PathStep(HexFormat.of().parseHex(s.getHash()), "RIGHT".equals(s.getPosition())))
                .toList();
        byte[] recomputedRoot = MerkleTree.rootFromAuditPath(leaf, path);

        assertThat(HexFormat.of().formatHex(recomputedRoot)).isEqualToIgnoringCase(proof.getRootHash());
    }

    @Test
    void buildProofDetectsATamperedRecordAfterSealing() {
        Source source = sourceRepository.save(new Source("agent-1", "irrelevant-key"));
        UUID attestationId = createAttestation(source.getId(), 1, "a");
        merkleService().sealNewLeaves();

        // A malicious operator rewrites the stored sequence after sealing, without
        // updating the frozen Merkle leaf - buildProof must catch the mismatch.
        Attestation row = attestationRepository.findById(attestationId).orElseThrow();
        row.setSequenceNr(999L);
        attestationRepository.save(row);

        ProofResponse proof = merkleService().buildProof(attestationId);

        assertThat(proof.getProvenanceIntact()).isFalse();
        assertThat(proof.getMessage()).contains("PROVENANCE MISMATCH");
    }

    @Test
    void buildProofReportsAnAttachedAnchorAndFailsClosedOnAnInvalidToken() {
        Source source = sourceRepository.save(new Source("agent-1", "irrelevant-key"));
        UUID attestationId = createAttestation(source.getId(), 1, "a");
        merkleService().sealNewLeaves();
        MerkleRoot root = merkleRootRepository.findTopByOrderByComputedAtDesc().orElseThrow();

        Anchor anchor = new Anchor();
        anchor.setRoot(root);
        anchor.setAuthority("test-tsa");
        anchor.setToken("not a real RFC 3161 token".getBytes());
        anchor.setAnchoredAt(Instant.now());
        anchorRepository.save(anchor);

        ProofResponse proof = merkleService().buildProof(attestationId);

        assertThat(proof.isAnchored()).isTrue();
        assertThat(proof.getAnchorAuthority()).isEqualTo("test-tsa");
        // Garbage token bytes: TsaVerifier fails closed rather than throwing.
        assertThat(proof.getAnchorSignatureValid()).isFalse();
        assertThat(proof.getAnchorTrusted()).isFalse();
    }

    @Test
    void buildProofThrowsNotFoundForAnUnknownAttestation() {
        assertThatThrownBy(() -> merkleService().buildProof(UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
