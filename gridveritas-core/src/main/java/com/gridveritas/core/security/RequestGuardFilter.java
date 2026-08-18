package com.gridveritas.core.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Set;

/**
 * Early request guard: rejects over-sized request bodies (413) and rate-limits by
 * client IP (429). Runs before the security chain so it also protects /auth/token.
 */
public class RequestGuardFilter implements Filter {

    private final RateLimiter rateLimiter;
    private final long maxBodyBytes;
    private final Set<String> trustedProxies;

    public RequestGuardFilter(RateLimiter rateLimiter, long maxBodyBytes, Set<String> trustedProxies) {
        this.rateLimiter = rateLimiter;
        this.maxBodyBytes = maxBodyBytes;
        this.trustedProxies = trustedProxies;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // Never throttle CORS preflight
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(req, res);
            return;
        }

        // Health and Prometheus scrapes must not compete with API rate-limit
        // buckets (ADR-013 replicas share Redis). Internal scrape interval is 15s.
        String uri = request.getRequestURI();
        if (uri != null && (uri.contains("/actuator/health") || uri.contains("/actuator/prometheus"))) {
            chain.doFilter(req, res);
            return;
        }

        // Body-size cap (Content-Length based; chunked bodies without CL are not capped here)
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBodyBytes) {
            write(response, 413, "payload_too_large",
                    "Request body exceeds the maximum of " + maxBodyBytes + " bytes.", null);
            return;
        }

        // Rate limit
        String ip = this.clientIp(request);
        boolean isAuth = "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().endsWith("/api/v1/auth/token");
        RateLimiter.Bucket bucket = isAuth ? RateLimiter.Bucket.AUTH : RateLimiter.Bucket.GENERAL;

        if (!rateLimiter.tryConsume(bucket, ip)) {
            write(response, 429, "too_many_requests",
                    "Rate limit exceeded. Please retry shortly.", "5");
            return;
        }

        chain.doFilter(req, res);
    }

    /**
     * X-Forwarded-For is only trusted when the request's direct peer is a
     * configured reverse proxy (REACH-1000) - otherwise any caller could rotate
     * the header to get a fresh rate-limit bucket on every request. With no
     * trusted proxies configured, the connecting socket's address is always
     * used, which is safe by default at the cost of not working correctly
     * behind an unconfigured reverse proxy.
     */
    private String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (trustedProxies.contains(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                int comma = xff.indexOf(',');
                return (comma > 0 ? xff.substring(0, comma) : xff).trim();
            }
        }
        return remoteAddr;
    }

    private static void write(HttpServletResponse response, int status, String error,
                              String message, String retryAfterSeconds) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        if (retryAfterSeconds != null) {
            response.setHeader("Retry-After", retryAfterSeconds);
        }
        response.getWriter().write(
                "{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}
