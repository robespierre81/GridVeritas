package com.gridveritas.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gridveritas.core.crypto.Ed25519Verifier;
import com.gridveritas.core.crypto.FederationCanon;
import com.gridveritas.core.crypto.TsaVerifier;
import com.gridveritas.core.domain.Anchor;
import com.gridveritas.core.domain.FederationPeer;
import com.gridveritas.core.domain.MerkleRoot;
import com.gridveritas.core.domain.PeerRoot;
import com.gridveritas.core.repository.AnchorRepository;
import com.gridveritas.core.repository.FederationPeerRepository;
import com.gridveritas.core.repository.MerkleRootRepository;
import com.gridveritas.core.repository.PeerRootRepository;
import com.gridveritas.core.web.dto.FederationDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Publish this operator's signed, anchored roots and fetch/verify peer roots
 * locally (M13). Peers are not trusted — Ed25519 + RFC 3161 are.
 */
@Service
public class FederationService {

    private static final Logger log = LoggerFactory.getLogger(FederationService.class);

    private final OperatorKeyService operatorKeys;
    private final MerkleRootRepository merkleRootRepository;
    private final AnchorRepository anchorRepository;
    private final FederationPeerRepository peerRepository;
    private final PeerRootRepository peerRootRepository;
    private final TsaVerifier tsaVerifier;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${gridveritas.federation.fetch-enabled:true}")
    private boolean fetchEnabled;

    @Value("${gridveritas.federation.fetch-timeout-ms:10000}")
    private int fetchTimeoutMs;

    @Value("${gridveritas.federation.publish-limit:50}")
    private int defaultPublishLimit;

    public FederationService(OperatorKeyService operatorKeys,
                             MerkleRootRepository merkleRootRepository,
                             AnchorRepository anchorRepository,
                             FederationPeerRepository peerRepository,
                             PeerRootRepository peerRootRepository,
                             TsaVerifier tsaVerifier,
                             AuditService auditService,
                             ObjectMapper objectMapper) {
        this.operatorKeys = operatorKeys;
        this.merkleRootRepository = merkleRootRepository;
        this.anchorRepository = anchorRepository;
        this.peerRepository = peerRepository;
        this.peerRootRepository = peerRootRepository;
        this.tsaVerifier = tsaVerifier;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public FederationDtos.OperatorInfo info() {
        FederationDtos.OperatorInfo info = new FederationDtos.OperatorInfo();
        info.operatorId = operatorKeys.operatorId();
        info.publicKey = operatorKeys.publicKeyBase64();
        return info;
    }

    @Transactional(readOnly = true)
    public FederationDtos.PublishedBundle publish(Integer limit) {
        int n = (limit == null || limit < 1) ? defaultPublishLimit : Math.min(limit, 200);
        FederationDtos.PublishedBundle bundle = new FederationDtos.PublishedBundle();
        bundle.operatorId = operatorKeys.operatorId();
        bundle.publicKey = operatorKeys.publicKeyBase64();
        List<MerkleRoot> roots = merkleRootRepository.findAllByOrderByComputedAtDesc(PageRequest.of(0, n));
        for (MerkleRoot root : roots) {
            bundle.roots.add(toPublished(root));
        }
        return bundle;
    }

    private FederationDtos.PublishedRoot toPublished(MerkleRoot root) {
        FederationDtos.PublishedRoot out = new FederationDtos.PublishedRoot();
        out.rootHash = root.getRootHash();
        out.prevRootHash = root.getPrevRootHash();
        out.leafCount = root.getLeafCount();
        out.computedAt = root.getComputedAt();
        byte[] message = FederationCanon.canonicalRoot(
                operatorKeys.operatorId().toString(),
                root.getRootHash(),
                root.getPrevRootHash(),
                root.getLeafCount(),
                root.getComputedAt());
        out.signature = operatorKeys.sign(message);
        Optional<Anchor> anchor = anchorRepository.findFirstByRootIdOrderByAnchoredAtAsc(root.getId());
        if (anchor.isPresent()) {
            Anchor a = anchor.get();
            FederationDtos.AnchorView av = new FederationDtos.AnchorView();
            av.authority = a.getAuthority();
            av.token = Base64.getEncoder().encodeToString(a.getToken());
            av.genTime = a.getGenTime();
            av.serial = a.getSerialNumber();
            out.anchor = av;
        }
        return out;
    }

    public FederationDtos.VerifyResult verify(FederationDtos.VerifyRequest req) {
        return verifyFields(req.operatorId, req.publicKey, req.rootHash, req.prevRootHash,
                req.leafCount, req.computedAt, req.signature, req.anchorToken);
    }

    FederationDtos.VerifyResult verifyFields(String operatorId,
                                             String publicKey,
                                             String rootHash,
                                             String prevRootHash,
                                             int leafCount,
                                             Instant computedAt,
                                             String signature,
                                             String anchorTokenB64) {
        FederationDtos.VerifyResult result = new FederationDtos.VerifyResult();
        byte[] message = FederationCanon.canonicalRoot(
                operatorId, rootHash, prevRootHash, leafCount, computedAt);
        result.signatureValid = Ed25519Verifier.verify(publicKey, message, signature);
        if (anchorTokenB64 != null && !anchorTokenB64.isBlank()) {
            result.anchorPresent = true;
            try {
                byte[] token = Base64.getDecoder().decode(anchorTokenB64.trim());
                byte[] digest = HexFormat.of().parseHex(rootHash);
                TsaVerifier.Result vr = tsaVerifier.verify(token, digest);
                result.anchorValid = vr.valid();
                result.detail = vr.detail();
            } catch (Exception e) {
                result.anchorValid = false;
                result.detail = "anchor token could not be decoded or verified: " + e.getMessage();
            }
        } else {
            result.anchorPresent = false;
            result.anchorValid = false;
            result.detail = result.signatureValid
                    ? "signature valid; no RFC 3161 token attached"
                    : "operator signature invalid";
        }
        if (result.signatureValid && result.anchorValid) {
            result.detail = "signature and RFC 3161 anchor verified locally";
        } else if (result.signatureValid && result.anchorPresent && !result.anchorValid) {
            result.detail = "signature valid; anchor failed: " + result.detail;
        }
        return result;
    }

    @Transactional
    public FederationDtos.PeerView addPeer(FederationDtos.RegisterPeerRequest req) {
        FederationPeer peer = new FederationPeer();
        peer.setName(req.name.trim());
        peer.setBaseUrl(trimSlash(req.baseUrl.trim()));
        peer.setPublicKey(req.publicKey.trim());
        peer.setEnabled(req.enabled == null || req.enabled);
        peer.setCreatedAt(Instant.now());
        FederationPeer saved = peerRepository.save(peer);
        auditService.recordAudit("FEDERATION_PEER_ADDED", saved.getId().toString(),
                "name=" + saved.getName() + " url=" + saved.getBaseUrl());
        return toPeerView(saved);
    }

    @Transactional(readOnly = true)
    public List<FederationDtos.PeerView> listPeers() {
        return peerRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(FederationService::toPeerView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FederationDtos.PeerRootView> listPeerRoots(int limit) {
        return peerRootRepository.findAllByOrderByFetchedAtDesc(PageRequest.of(0, Math.min(limit, 200)))
                .stream()
                .map(FederationService::toPeerRootView)
                .toList();
    }

    @Scheduled(fixedDelayString = "${gridveritas.federation.fetch-interval-ms:300000}")
    public void fetchEnabledPeers() {
        if (!fetchEnabled) {
            return;
        }
        for (FederationPeer peer : peerRepository.findByEnabledTrue()) {
            try {
                fetchPeer(peer.getId());
            } catch (Exception e) {
                log.warn("Scheduled fetch of peer {} failed: {}", peer.getName(), e.getMessage());
            }
        }
    }

    @Transactional
    public FederationDtos.FetchReport fetchPeer(UUID peerId) {
        FederationPeer peer = peerRepository.findById(peerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "peer not found"));
        FederationDtos.FetchReport report = new FederationDtos.FetchReport();
        report.peerId = peerId;
        try {
            FederationDtos.PublishedBundle bundle = pull(peer);
            if (bundle.publicKey == null || !bundle.publicKey.equals(peer.getPublicKey())) {
                throw new IllegalStateException("published public key does not match the registered peer key");
            }
            String operatorId = bundle.operatorId == null ? "" : bundle.operatorId.toString();
            for (FederationDtos.PublishedRoot root : bundle.roots) {
                report.seen++;
                if (peerRootRepository.existsByPeerIdAndRootHash(peer.getId(), root.rootHash)) {
                    report.alreadyKnown++;
                    continue;
                }
                String token = root.anchor == null ? null : root.anchor.token;
                FederationDtos.VerifyResult vr = verifyFields(
                        operatorId, peer.getPublicKey(), root.rootHash, root.prevRootHash,
                        root.leafCount, root.computedAt, root.signature, token);
                if (!vr.signatureValid) {
                    report.signatureRejected++;
                }
                PeerRoot stored = new PeerRoot();
                stored.setPeer(peer);
                stored.setOperatorId(operatorId);
                stored.setRootHash(root.rootHash);
                stored.setPrevRootHash(root.prevRootHash);
                stored.setLeafCount(root.leafCount);
                stored.setComputedAt(root.computedAt);
                stored.setOperatorSignature(root.signature);
                if (root.anchor != null) {
                    stored.setAnchorAuthority(root.anchor.authority);
                    if (root.anchor.token != null) {
                        stored.setAnchorToken(Base64.getDecoder().decode(root.anchor.token));
                    }
                }
                stored.setSignatureValid(vr.signatureValid);
                stored.setAnchorValid(vr.anchorValid);
                stored.setFetchedAt(Instant.now());
                peerRootRepository.save(stored);
                report.stored++;
            }
            peer.setLastFetchedAt(Instant.now());
            peer.setLastError(null);
            peerRepository.save(peer);
            auditService.recordAudit("FEDERATION_FETCH", peer.getId().toString(),
                    "seen=" + report.seen + " stored=" + report.stored);
        } catch (Exception e) {
            report.error = e.getMessage();
            peer.setLastError(trimError(e.getMessage()));
            peerRepository.save(peer);
            log.warn("Fetch peer {} failed: {}", peer.getName(), e.getMessage());
        }
        return report;
    }

    private FederationDtos.PublishedBundle pull(FederationPeer peer) throws Exception {
        URI uri = resolveRootsUri(peer.getBaseUrl());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(fetchTimeoutMs))
                .GET()
                .header("Accept", "application/json")
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("peer returned HTTP " + response.statusCode());
        }
        return objectMapper.readValue(response.body(), FederationDtos.PublishedBundle.class);
    }

    static URI resolveRootsUri(String baseUrl) {
        String trimmed = trimSlash(baseUrl);
        if (trimmed.endsWith("/api/v1/federation/roots")) {
            return URI.create(trimmed);
        }
        if (trimmed.endsWith("/api/v1")) {
            return URI.create(trimmed + "/federation/roots");
        }
        return URI.create(trimmed + "/api/v1/federation/roots");
    }

    private static String trimSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String trimError(String msg) {
        if (msg == null) {
            return "unknown error";
        }
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }

    private static FederationDtos.PeerView toPeerView(FederationPeer peer) {
        FederationDtos.PeerView v = new FederationDtos.PeerView();
        v.id = peer.getId();
        v.name = peer.getName();
        v.baseUrl = peer.getBaseUrl();
        v.publicKey = peer.getPublicKey();
        v.enabled = peer.isEnabled();
        v.lastFetchedAt = peer.getLastFetchedAt();
        v.lastError = peer.getLastError();
        v.createdAt = peer.getCreatedAt();
        return v;
    }

    private static FederationDtos.PeerRootView toPeerRootView(PeerRoot root) {
        FederationDtos.PeerRootView v = new FederationDtos.PeerRootView();
        v.id = root.getId();
        v.peerId = root.getPeer().getId();
        v.peerName = root.getPeer().getName();
        v.operatorId = root.getOperatorId();
        v.rootHash = root.getRootHash();
        v.prevRootHash = root.getPrevRootHash();
        v.leafCount = root.getLeafCount();
        v.computedAt = root.getComputedAt();
        v.signatureValid = root.isSignatureValid();
        v.anchorValid = root.isAnchorValid();
        v.anchorAuthority = root.getAnchorAuthority();
        v.fetchedAt = root.getFetchedAt();
        return v;
    }
}
