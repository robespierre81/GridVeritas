package com.gridveritas.core.web;

import com.gridveritas.core.repository.AnchorRepository;
import com.gridveritas.core.repository.AnomalyFindingRepository;
import com.gridveritas.core.repository.AttestationRepository;
import com.gridveritas.core.repository.AuditLogRepository;
import com.gridveritas.core.repository.MerkleRootRepository;
import com.gridveritas.core.repository.PeerRootRepository;
import com.gridveritas.core.repository.SettlementRecordRepository;
import com.gridveritas.core.repository.SourceRepository;
import com.gridveritas.core.repository.VerificationEventRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Aggregate counts for the overview dashboard. Read-accessible (JWT or read key). */
@RestController
@RequestMapping("/api/v1")
public class StatsController {

    private final SourceRepository sourceRepository;
    private final AttestationRepository attestationRepository;
    private final MerkleRootRepository merkleRootRepository;
    private final AnchorRepository anchorRepository;
    private final AnomalyFindingRepository anomalyRepository;
    private final VerificationEventRepository verificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final PeerRootRepository peerRootRepository;
    private final SettlementRecordRepository settlementRecordRepository;

    public StatsController(SourceRepository sourceRepository,
                           AttestationRepository attestationRepository,
                           MerkleRootRepository merkleRootRepository,
                           AnchorRepository anchorRepository,
                           AnomalyFindingRepository anomalyRepository,
                           VerificationEventRepository verificationRepository,
                           AuditLogRepository auditLogRepository,
                           PeerRootRepository peerRootRepository,
                           SettlementRecordRepository settlementRecordRepository) {
        this.sourceRepository = sourceRepository;
        this.attestationRepository = attestationRepository;
        this.merkleRootRepository = merkleRootRepository;
        this.anchorRepository = anchorRepository;
        this.anomalyRepository = anomalyRepository;
        this.verificationRepository = verificationRepository;
        this.auditLogRepository = auditLogRepository;
        this.peerRootRepository = peerRootRepository;
        this.settlementRecordRepository = settlementRecordRepository;
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("sources", sourceRepository.count());
        m.put("attestations", attestationRepository.count());
        m.put("merkleRoots", merkleRootRepository.count());
        m.put("anchors", anchorRepository.count());
        m.put("anomalies", anomalyRepository.count());
        m.put("verifications", verificationRepository.count());
        m.put("auditEvents", auditLogRepository.count());
        m.put("peerRoots", peerRootRepository.count());
        m.put("verifiedPeerRoots", peerRootRepository.countBySignatureValidTrueAndAnchorValidTrue());
        m.put("settlements", settlementRecordRepository.count());
        return m;
    }
}
