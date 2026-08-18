package com.gridveritas.core.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RequestGuardFilter is the first line of defense (body-size cap + rate limiting)
 * and runs before Spring Security, so /auth/token is covered too. The
 * X-Forwarded-For trust-boundary tests pin the REACH-1000 fix: an unauthenticated
 * caller must not be able to get a fresh rate-limit bucket per request just by
 * sending a different X-Forwarded-For value.
 */
class RequestGuardFilterTest {

    private static RequestGuardFilter filter(RateLimiter limiter, long maxBodyBytes, Set<String> trustedProxies) {
        return new RequestGuardFilter(limiter, maxBodyBytes, trustedProxies);
    }

    private static MockHttpServletRequest postRequest(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr("203.0.113.7");
        return request;
    }

    @Test
    void requestsUnderTheLimitPassThrough() throws Exception {
        RequestGuardFilter filter = filter(new RateLimiter(10, 10, new InMemoryRateLimitStore(1000)), 1024, Set.of());
        MockHttpServletRequest request = postRequest("/api/v1/attestations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200); // MockHttpServletResponse defaults to 200
    }

    @Test
    void oversizedBodyIsRejectedWith413() throws Exception {
        RequestGuardFilter filter = filter(new RateLimiter(10, 10, new InMemoryRateLimitStore(1000)), 100, Set.of());
        MockHttpServletRequest request = postRequest("/api/v1/attestations");
        request.setContent(new byte[200]); // sets Content-Length to 200

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull(); // never reached downstream
    }

    @Test
    void exceedingTheGeneralRateLimitIsRejectedWith429() throws Exception {
        RequestGuardFilter filter = filter(new RateLimiter(1, 10, new InMemoryRateLimitStore(1000)), 1024, Set.of());

        MockHttpServletRequest first = postRequest("/api/v1/attestations");
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletRequest second = postRequest("/api/v1/attestations");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, new MockFilterChain());

        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("5");
    }

    @Test
    void authTokenRequestsUseTheirOwnStricterBucket() throws Exception {
        // General bucket has plenty of room, but AUTH is capped at 1/minute -
        // hitting /auth/token must be throttled independently of GENERAL traffic.
        RequestGuardFilter filter = filter(new RateLimiter(100, 1, new InMemoryRateLimitStore(1000)), 1024, Set.of());

        MockHttpServletRequest first = postRequest("/api/v1/auth/token");
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletRequest second = postRequest("/api/v1/auth/token");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, new MockFilterChain());

        assertThat(secondResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void prometheusAndHealthScrapesAreNotRateLimited() throws Exception {
        RequestGuardFilter filter = filter(new RateLimiter(0, 0, new InMemoryRateLimitStore(1000)), 1024, Set.of());

        MockHttpServletRequest prom = new MockHttpServletRequest("GET", "/actuator/prometheus");
        prom.setRemoteAddr("10.0.0.8");
        MockFilterChain promChain = new MockFilterChain();
        filter.doFilter(prom, new MockHttpServletResponse(), promChain);
        assertThat(promChain.getRequest()).isNotNull();

        MockHttpServletRequest health = new MockHttpServletRequest("GET", "/actuator/health");
        health.setRemoteAddr("10.0.0.8");
        MockFilterChain healthChain = new MockFilterChain();
        filter.doFilter(health, new MockHttpServletResponse(), healthChain);
        assertThat(healthChain.getRequest()).isNotNull();
    }

    @Test
    void optionsRequestsAreNeverThrottled() throws Exception {
        RequestGuardFilter filter = filter(new RateLimiter(0, 0, new InMemoryRateLimitStore(1000)), 1024, Set.of());
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/attestations");
        request.setRemoteAddr("203.0.113.7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void xForwardedForIsIgnoredWhenThePeerIsNotATrustedProxy() throws Exception {
        // REACH-1000: with no trusted proxies configured, spoofing a different
        // X-Forwarded-For value on every request must not yield a fresh bucket -
        // the real socket address (the same on every request here) is used instead.
        RequestGuardFilter filter = filter(new RateLimiter(1, 10, new InMemoryRateLimitStore(1000)), 1024, Set.of());

        MockHttpServletRequest first = postRequest("/api/v1/attestations");
        first.addHeader("X-Forwarded-For", "1.1.1.1");
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletRequest second = postRequest("/api/v1/attestations"); // same remoteAddr, different XFF
        second.addHeader("X-Forwarded-For", "2.2.2.2");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, new MockFilterChain());

        assertThat(secondResponse.getStatus())
                .as("both requests share the same real peer address, so the second should be throttled")
                .isEqualTo(429);
    }

    @Test
    void xForwardedForIsHonoredWhenThePeerIsAConfiguredTrustedProxy() throws Exception {
        RequestGuardFilter filter = filter(new RateLimiter(1, 10, new InMemoryRateLimitStore(1000)), 1024, Set.of("203.0.113.7"));

        MockHttpServletRequest first = postRequest("/api/v1/attestations"); // remoteAddr = trusted proxy
        first.addHeader("X-Forwarded-For", "1.1.1.1");
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletRequest second = postRequest("/api/v1/attestations");
        second.addHeader("X-Forwarded-For", "2.2.2.2"); // different real client behind the same trusted proxy
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, new MockFilterChain());

        assertThat(secondResponse.getStatus())
                .as("different real clients behind a trusted proxy get independent buckets")
                .isEqualTo(200);
    }
}
