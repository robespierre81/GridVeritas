package com.gridveritas.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Map;

/**
 * When enabled, requires a TLS client certificate (mTLS) for attestation ingest.
 * Complements the INGEST JWT: ingest then needs both a client cert and the role.
 * Off by default so the plain-HTTP dev stack is unaffected.
 */
@Component
public class MtlsIngestFilter extends OncePerRequestFilter {

    private final boolean requireForIngest;
    private final ObjectMapper mapper;

    public MtlsIngestFilter(@Value("${gridveritas.security.mtls.require-for-ingest:false}") boolean requireForIngest,
                            ObjectMapper mapper) {
        this.requireForIngest = requireForIngest;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (requireForIngest
                && "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().endsWith("/api/v1/attestations")) {
            X509Certificate[] certs =
                    (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");
            if (certs == null || certs.length == 0) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                mapper.writeValue(response.getWriter(), Map.of(
                        "error", "client_certificate_required",
                        "message", "Attestation ingest requires a TLS client certificate (mTLS)."));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
