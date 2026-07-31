package com.gridveritas.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Append-only record of security/configuration events. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String action;      // SOURCE_CREATED | TOKEN_ISSUED | ANCHOR_CREATED | ...

    @Column(nullable = false, length = 64)
    private String principal;

    @Column(length = 128)
    private String target;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getPrincipal() { return principal; }
    public void setPrincipal(String principal) { this.principal = principal; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
