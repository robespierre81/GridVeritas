package com.gridveritas.core.web;

import com.gridveritas.core.security.JwtService;
import com.gridveritas.core.service.AuditService;
import com.gridveritas.core.web.dto.TokenRequest;
import com.gridveritas.core.web.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtService jwtService;
    private final AuditService auditService;
    private final String adminPassword;
    private final String ingestPassword;

    public AuthController(JwtService jwtService,
                          AuditService auditService,
                          @Value("${gridveritas.security.users.admin-password}") String adminPassword,
                          @Value("${gridveritas.security.users.ingest-password}") String ingestPassword) {
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.adminPassword = adminPassword;
        this.ingestPassword = ingestPassword;
    }

    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        String role = roleFor(request.getUsername(), request.getPassword());
        if (role == null) {
            auditService.recordAudit("TOKEN_DENIED", request.getUsername(), "invalid credentials");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        String jwt = jwtService.issue(request.getUsername(), role);
        auditService.recordAudit("TOKEN_ISSUED", request.getUsername(), "role=" + role);
        return new TokenResponse(jwt, role, jwtService.ttlSeconds());
    }

    private String roleFor(String username, String password) {
        if ("admin".equals(username) && constantTimeEquals(password, adminPassword)) {
            return "ADMIN";
        }
        if ("ingest".equals(username) && constantTimeEquals(password, ingestPassword)) {
            return "INGEST";
        }
        return null;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
