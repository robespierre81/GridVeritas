package com.gridveritas.core.service;

import com.gridveritas.core.crypto.TsaClient;
import com.gridveritas.core.crypto.TsaVerifier;
import com.gridveritas.core.domain.Anchor;
import com.gridveritas.core.domain.MerkleRoot;
import com.gridveritas.core.repository.AnchorRepository;
import com.gridveritas.core.repository.MerkleRootRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Anchors not-yet-anchored Merkle roots to an external RFC 3161 TSA, verifying the
 * returned token's signature (and trust, if pinned) before storing it. Runs
 * separately from sealing so the network call is outside any DB transaction.
 */
@Service
public class AnchorService {

    private static final Logger log = LoggerFactory.getLogger(AnchorService.class);

    private final MerkleRootRepository merkleRootRepository;
    private final AnchorRepository anchorRepository;
    private final TsaClient tsaClient;
    private final TsaVerifier tsaVerifier;
    private final AuditService auditService;

    @Value("${gridveritas.anchor.max-batch:50}")
    private int maxBatch;

    public AnchorService(MerkleRootRepository merkleRootRepository,
                         AnchorRepository anchorRepository,
                         TsaClient tsaClient,
                         TsaVerifier tsaVerifier,
                         AuditService auditService) {
        this.merkleRootRepository = merkleRootRepository;
        this.anchorRepository = anchorRepository;
        this.tsaClient = tsaClient;
        this.tsaVerifier = tsaVerifier;
        this.auditService = auditService;
    }

    @Scheduled(fixedDelayString = "${gridveritas.anchor.interval-ms:120000}")
    public void anchorPendingRoots() {
        List<MerkleRoot> pending =
                merkleRootRepository.findWithoutAnchor(PageRequest.of(0, maxBatch));
        for (MerkleRoot root : pending) {
            try {
                byte[] rootDigest = HexFormat.of().parseHex(root.getRootHash());
                TsaClient.Result res = tsaClient.timestamp(rootDigest);

                TsaVerifier.Result vr = tsaVerifier.verify(res.token(), rootDigest);
                if (!vr.valid()) {
                    log.warn("TSA token for root {} failed verification ({}); not storing.",
                            root.getRootHash(), vr.detail());
                    continue;
                }

                Anchor anchor = new Anchor();
                anchor.setRoot(root);
                anchor.setAuthority(res.authority());
                anchor.setToken(res.token());
                anchor.setGenTime(res.genTime());
                anchor.setSerialNumber(res.serialNumber());
                anchor.setAnchoredAt(Instant.now());
                anchorRepository.save(anchor);
                auditService.recordAudit("ANCHOR_CREATED", root.getRootHash(),
                        "authority=" + res.authority() + (vr.trusted() ? "; trust-pinned" : ""));

                log.info("Anchored root {} via {} at {} ({})",
                        root.getRootHash(), res.authority(), res.genTime(),
                        vr.trusted() ? "trust-pinned" : "signature-verified, trust not pinned");
            } catch (Exception e) {
                log.warn("Anchoring root {} failed (will retry next run): {}",
                        root.getRootHash(), e.getMessage());
            }
        }
    }
}
