package com.gridveritas.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Append-only record of a verification action (VERIFY / PROOF) and its result. */
@Entity
@Table(name = "verification_events")
public class VerificationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;   // VERIFY | PROOF

    @Column(length = 128)
    private String subject;     // payload hash or attestation id

    @Column(nullable = false, length = 40)
    private String result;      // VALID | INVALID | NOT_FOUND | SEALED | PENDING_SEAL | ...

    @Column(nullable = false, length = 64)
    private String principal;   // authenticated username, or "system"

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getPrincipal() { return principal; }
    public void setPrincipal(String principal) { this.principal = principal; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
