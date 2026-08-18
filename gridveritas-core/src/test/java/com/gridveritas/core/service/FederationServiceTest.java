package com.gridveritas.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gridveritas.core.crypto.Ed25519Keys;
import com.gridveritas.core.crypto.FederationCanon;
import com.gridveritas.core.crypto.TsaVerifier;
import com.gridveritas.core.domain.FederationPeer;
import com.gridveritas.core.domain.MerkleRoot;
import com.gridveritas.core.domain.PeerRoot;
import com.gridveritas.core.repository.AnchorRepository;
import com.gridveritas.core.repository.AuditLogRepository;
import com.gridveritas.core.repository.FederationPeerRepository;
import com.gridveritas.core.repository.MerkleRootRepository;
import com.gridveritas.core.repository.PeerRootRepository;
import com.gridveritas.core.repository.VerificationEventRepository;
import com.gridveritas.core.web.dto.FederationDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class FederationServiceTest {

    @TempDir
    Path operatorDir;

    @Autowired
    private MerkleRootRepository merkleRootRepository;
    @Autowired
    private AnchorRepository anchorRepository;
    @Autowired
    private FederationPeerRepository peerRepository;
    @Autowired
    private PeerRootRepository peerRootRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private VerificationEventRepository verificationEventRepository;

    private OperatorKeyService operatorKeys;
    private FederationService federationService;

    @BeforeEach
    void setUp() {
        operatorKeys = new OperatorKeyService(operatorDir.toString());
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        federationService = new FederationService(
                operatorKeys,
                merkleRootRepository,
                anchorRepository,
                peerRepository,
                peerRootRepository,
                new TsaVerifier(""),
                new AuditService(verificationEventRepository, auditLogRepository),
                mapper);
        ReflectionTestUtils.setField(federationService, "fetchEnabled", false);
        ReflectionTestUtils.setField(federationService, "fetchTimeoutMs", 1000);
        ReflectionTestUtils.setField(federationService, "defaultPublishLimit", 50);
    }

    @Test
    void publishSignsEachRootAndVerifyAcceptsOwnKey() {
        MerkleRoot root = new MerkleRoot();
        root.setRootHash("ab".repeat(32));
        root.setPrevRootHash(null);
        root.setLeafCount(3);
        root.setComputedAt(Instant.parse("2026-08-18T10:00:00Z"));
        merkleRootRepository.save(root);

        FederationDtos.PublishedBundle bundle = federationService.publish(10);
        assertThat(bundle.operatorId).isEqualTo(operatorKeys.operatorId());
        assertThat(bundle.publicKey).isEqualTo(operatorKeys.publicKeyBase64());
        assertThat(bundle.roots).hasSize(1);
        FederationDtos.PublishedRoot published = bundle.roots.get(0);

        FederationDtos.VerifyRequest req = new FederationDtos.VerifyRequest();
        req.operatorId = bundle.operatorId.toString();
        req.publicKey = bundle.publicKey;
        req.rootHash = published.rootHash;
        req.prevRootHash = published.prevRootHash;
        req.leafCount = published.leafCount;
        req.computedAt = published.computedAt;
        req.signature = published.signature;

        FederationDtos.VerifyResult ok = federationService.verify(req);
        assertThat(ok.signatureValid).isTrue();
        assertThat(ok.anchorPresent).isFalse();
    }

    @Test
    void verifyRejectsADifferentOperatorKey() {
        Instant at = Instant.parse("2026-08-18T10:00:00Z");
        String hash = "cd".repeat(32);
        byte[] message = FederationCanon.canonicalRoot(
                operatorKeys.operatorId().toString(), hash, null, 1, at);
        String signature = operatorKeys.sign(message);

        KeyPair other = Ed25519Keys.generate();
        FederationDtos.VerifyRequest req = new FederationDtos.VerifyRequest();
        req.operatorId = operatorKeys.operatorId().toString();
        req.publicKey = Ed25519Keys.publicKeyBase64(other.getPublic());
        req.rootHash = hash;
        req.leafCount = 1;
        req.computedAt = at;
        req.signature = signature;

        assertThat(federationService.verify(req).signatureValid).isFalse();
    }

    @Test
    void fetchStoresPeerRootWithLocalVerificationFlags() {
        FederationPeer peer = new FederationPeer();
        peer.setName("peer-a");
        peer.setBaseUrl("http://peer.example");
        peer.setPublicKey(operatorKeys.publicKeyBase64());
        peer.setEnabled(true);
        peer.setCreatedAt(Instant.now());
        peer = peerRepository.save(peer);

        Instant at = Instant.parse("2026-08-18T11:00:00Z");
        String hash = "ef".repeat(32);
        String operatorId = operatorKeys.operatorId().toString();
        String signature = operatorKeys.sign(
                FederationCanon.canonicalRoot(operatorId, hash, null, 2, at));

        PeerRoot stored = new PeerRoot();
        stored.setPeer(peer);
        stored.setOperatorId(operatorId);
        stored.setRootHash(hash);
        stored.setLeafCount(2);
        stored.setComputedAt(at);
        stored.setOperatorSignature(signature);
        FederationDtos.VerifyResult vr = federationService.verifyFields(
                operatorId, peer.getPublicKey(), hash, null, 2, at, signature, null);
        stored.setSignatureValid(vr.signatureValid);
        stored.setAnchorValid(vr.anchorValid);
        stored.setFetchedAt(Instant.now());
        peerRootRepository.save(stored);

        assertThat(vr.signatureValid).isTrue();
        assertThat(peerRootRepository.existsByPeerIdAndRootHash(peer.getId(), hash)).isTrue();
        assertThat(peerRootRepository.countBySignatureValidTrueAndAnchorValidTrue()).isZero();
        assertThat(federationService.listPeerRoots(10)).hasSize(1);
        assertThat(federationService.listPeerRoots(10).get(0).peerName).isEqualTo("peer-a");
    }

    @Test
    void addPeerPersistsRegistryRow() {
        FederationDtos.RegisterPeerRequest req = new FederationDtos.RegisterPeerRequest();
        req.name = "iso-lab";
        req.baseUrl = "https://peer.example/gridveritas/";
        req.publicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
        FederationDtos.PeerView view = federationService.addPeer(req);
        assertThat(view.id).isNotNull();
        assertThat(view.baseUrl).isEqualTo("https://peer.example/gridveritas");
        assertThat(federationService.listPeers()).extracting(p -> p.name).containsExactly("iso-lab");
    }

    @Test
    void resolveRootsUriAcceptsBaseOrFullPath() {
        assertThat(FederationService.resolveRootsUri("http://x:8080").toString())
                .isEqualTo("http://x:8080/api/v1/federation/roots");
        assertThat(FederationService.resolveRootsUri("http://x:8080/api/v1").toString())
                .isEqualTo("http://x:8080/api/v1/federation/roots");
        assertThat(FederationService.resolveRootsUri("http://x:8080/api/v1/federation/roots").toString())
                .isEqualTo("http://x:8080/api/v1/federation/roots");
    }

    @Test
    void operatorKeySurvivesReloadFromTheSameDirectory() {
        UUID first = operatorKeys.operatorId();
        String pub = operatorKeys.publicKeyBase64();
        OperatorKeyService reloaded = new OperatorKeyService(operatorDir.toString());
        assertThat(reloaded.operatorId()).isEqualTo(first);
        assertThat(reloaded.publicKeyBase64()).isEqualTo(pub);
    }
}
