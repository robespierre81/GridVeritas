package com.gridveritas.core.service;

import com.gridveritas.core.domain.AuditLog;
import com.gridveritas.core.domain.VerificationEvent;
import com.gridveritas.core.repository.AuditLogRepository;
import com.gridveritas.core.repository.VerificationEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the verification and audit trail. Recording is best-effort and runs in
 * its own transaction (REQUIRES_NEW) so it works from read-only callers and never
 * breaks the main flow if it fails.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final VerificationEventRepository verificationRepo;
    private final AuditLogRepository auditRepo;

    public AuditService(VerificationEventRepository verificationRepo, AuditLogRepository auditRepo) {
        this.verificationRepo = verificationRepo;
        this.auditRepo = auditRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordVerification(String eventType, String subject, String result, String detail) {
        try {
            VerificationEvent e = new VerificationEvent();
            e.setEventType(eventType);
            e.setSubject(subject);
            e.setResult(result);
            e.setDetail(detail);
            e.setPrincipal(currentPrincipal());
            verificationRepo.save(e);
        } catch (Exception ex) {
            log.warn("Failed to record verification event: {}", ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAudit(String action, String target, String detail) {
        try {
            AuditLog a = new AuditLog();
            a.setAction(action);
            a.setTarget(target);
            a.setDetail(detail);
            a.setPrincipal(currentPrincipal());
            auditRepo.save(a);
        } catch (Exception ex) {
            log.warn("Failed to record audit event: {}", ex.getMessage());
        }
    }

    private String currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
            return auth.getName();
        }
        return "system";
    }
}
