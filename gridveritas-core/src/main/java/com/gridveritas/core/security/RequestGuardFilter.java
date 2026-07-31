package com.gridveritas.core.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Early request guard: rejects over-sized request bodies (413) and rate-limits by
 * client IP (429). Runs before the security chain so it also protects /auth/token.
 */
public class RequestGuardFilter implements Filter {

    private final RateLimiter rateLimiter;
    private final long maxBodyBytes;

    public RequestGuardFilter(RateLimiter rateLimiter, long maxBodyBytes) {
        this.rateLimiter = rateLimiter;
        this.maxBodyBytes = maxBodyBytes;
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

        // Body-size cap (Content-Length based; chunked bodies without CL are not capped here)
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBodyBytes) {
            write(response, 413, "payload_too_large",
                    "Request body exceeds the maximum of " + maxBodyBytes + " bytes.", null);
            return;
        }

        // Rate limit
        String ip = clientIp(request);
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

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
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
