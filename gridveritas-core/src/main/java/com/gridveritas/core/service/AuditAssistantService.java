package com.gridveritas.core.service;

import com.gridveritas.core.ai.OllamaClient;
import com.gridveritas.core.domain.AnomalyFinding;
import com.gridveritas.core.domain.AuditLog;
import com.gridveritas.core.domain.Source;
import com.gridveritas.core.domain.VerificationEvent;
import com.gridveritas.core.repository.AnchorRepository;
import com.gridveritas.core.repository.AnomalyFindingRepository;
import com.gridveritas.core.repository.AuditLogRepository;
import com.gridveritas.core.repository.AttestationRepository;
import com.gridveritas.core.repository.MerkleRootRepository;
import com.gridveritas.core.repository.VerificationEventRepository;
import com.gridveritas.core.repository.SourceRepository;
import com.gridveritas.core.web.dto.AuditAnswerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Natural-language audit assistant (RAG-lite). Retrieves current facts from the
 * database, grounds a local LLM (Ollama) on them, and returns the answer plus the
 * facts it was grounded on. The LLM is used ONLY for phrasing answers over
 * retrieved facts — not for anomaly detection (ADR-004).
 */
@Service
public class AuditAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AuditAssistantService.class);

    private static final String SYSTEM_TEMPLATE = """
            You are the GridVeritas audit assistant. Answer the user's question using ONLY the
            facts listed below. If the facts do not contain the answer, say you don't have that
            information — do not invent numbers, sources, or events. Be concise and factual.

            FACTS:
            %s
            """;

    private final OllamaClient ollama;
    private final AttestationRepository attestationRepository;
    private final SourceRepository sourceRepository;
    private final MerkleRootRepository merkleRootRepository;
    private final AnchorRepository anchorRepository;
    private final AnomalyFindingRepository anomalyRepository;
    private final VerificationEventRepository verificationRepository;
    private final AuditLogRepository auditLogRepository;

    public AuditAssistantService(OllamaClient ollama,
                                 AttestationRepository attestationRepository,
                                 SourceRepository sourceRepository,
                                 MerkleRootRepository merkleRootRepository,
                                 AnchorRepository anchorRepository,
                                 AnomalyFindingRepository anomalyRepository,
                                 VerificationEventRepository verificationRepository,
                                 AuditLogRepository auditLogRepository) {
        this.ollama = ollama;
        this.attestationRepository = attestationRepository;
        this.sourceRepository = sourceRepository;
        this.merkleRootRepository = merkleRootRepository;
        this.anchorRepository = anchorRepository;
        this.anomalyRepository = anomalyRepository;
        this.verificationRepository = verificationRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public AuditAnswerResponse ask(String question) {
        AuditAnswerResponse resp = new AuditAnswerResponse();
        resp.setModel(ollama.getModel());

        String context = buildContext();
        resp.setContextUsed(context);

        if (!ollama.isEnabled()) {
            resp.setAnswered(false);
            resp.setNote("Audit assistant is disabled (gridveritas.ai.ollama.enabled=false).");
            return resp;
        }
        if (!ollama.isAvailable()) {
            resp.setAnswered(false);
            resp.setNote("Audit assistant is temporarily unavailable (Ollama not reachable). "
                    + "The retrieved facts are still provided in contextUsed.");
            return resp;
        }

        try {
            String answer = ollama.chat(String.format(SYSTEM_TEMPLATE, context), question);
            resp.setAnswered(true);
            resp.setAnswer(answer);
        } catch (Exception e) {
            log.warn("Audit assistant query failed: {}", e.getMessage());
            resp.setAnswered(false);
            resp.setNote("Audit assistant query failed: " + e.getMessage());
        }
        return resp;
    }

    /** Retrieval step: gather a bounded, current snapshot of facts from the database. */
    private String buildContext() {
        StringBuilder sb = new StringBuilder();

        long attestations = attestationRepository.count();
        long roots = merkleRootRepository.count();
        long anchors = anchorRepository.count();
        List<Source> sources = sourceRepository.findAll();

        sb.append("Totals: ")
                .append(attestations).append(" attestations, ")
                .append(sources.size()).append(" sources, ")
                .append(roots).append(" Merkle roots, ")
                .append(anchors).append(" external anchors.\n");

        sb.append("Sources:\n");
        for (Source s : sources) {
            sb.append("- ").append(s.getName())
                    .append(" (id=").append(s.getId())
                    .append(", status=").append(s.getStatus())
                    .append(", lastSeen=").append(s.getLastSeenAt())
                    .append(")\n");
        }

        List<AnomalyFinding> anomalies = anomalyRepository.findTop100ByOrderByDetectedAtDesc();
        int shown = Math.min(anomalies.size(), 15);
        sb.append("Recent anomaly findings (").append(anomalies.size())
                .append(" total, showing ").append(shown).append("):\n");
        for (int i = 0; i < shown; i++) {
            AnomalyFinding f = anomalies.get(i);
            sb.append("- [").append(f.getSeverity()).append("] ")
                    .append(f.getType()).append(": ")
                    .append(f.getDescription())
                    .append(" (at ").append(f.getDetectedAt()).append(")\n");
        }
        if (anomalies.isEmpty()) {
            sb.append("- none\n");
        }

        List<VerificationEvent> verifications = verificationRepository.findTop100ByOrderByCreatedAtDesc();
        int vShown = Math.min(verifications.size(), 10);
        sb.append("Recent verification events (").append(verifications.size())
                .append(" total, showing ").append(vShown).append("):\n");
        for (int i = 0; i < vShown; i++) {
            VerificationEvent v = verifications.get(i);
            sb.append("- ").append(v.getEventType()).append(" -> ").append(v.getResult())
                    .append(" by ").append(v.getPrincipal())
                    .append(" at ").append(v.getCreatedAt()).append("\n");
        }
        if (verifications.isEmpty()) {
            sb.append("- none\n");
        }

        List<AuditLog> auditEvents = auditLogRepository.findTop100ByOrderByCreatedAtDesc();
        int aShown = Math.min(auditEvents.size(), 10);
        sb.append("Recent audit events (").append(auditEvents.size())
                .append(" total, showing ").append(aShown).append("):\n");
        for (int i = 0; i < aShown; i++) {
            AuditLog a = auditEvents.get(i);
            sb.append("- ").append(a.getAction()).append(" ").append(a.getTarget())
                    .append(" by ").append(a.getPrincipal())
                    .append(" at ").append(a.getCreatedAt()).append("\n");
        }
        if (auditEvents.isEmpty()) {
            sb.append("- none\n");
        }

        return sb.toString();
    }
}
