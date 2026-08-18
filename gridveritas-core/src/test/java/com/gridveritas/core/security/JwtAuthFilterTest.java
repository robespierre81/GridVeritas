package com.gridveritas.core.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtAuthFilter is where a Bearer token becomes an authenticated principal +
 * ROLE_&lt;role&gt; authority for the rest of the security chain. It is
 * deliberately silent on failure (clears the context, lets the entry point 401
 * later), so these tests check the SecurityContext state directly rather than
 * the HTTP response.
 */
class JwtAuthFilterTest {

    private final JwtService jwtService = new JwtService("test-secret-value-not-used-in-prod", 30);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerTokenAuthenticatesWithARolePrefixedAuthority() throws Exception {
        String token = jwtService.issue("alice", "ADMIN");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("alice");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        assertThat(chain.getRequest()).isNotNull(); // always continues the chain
    }

    @Test
    void malformedTokenLeavesTheContextUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer not-a-real-jwt");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull(); // still continues - the entry point handles the 401
    }

    @Test
    void tokenFromADifferentSecretLeavesTheContextUnauthenticated() throws Exception {
        JwtService otherIssuer = new JwtService("a-completely-different-secret", 30);
        String token = otherIssuer.issue("mallory", "ADMIN");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingAuthorizationHeaderLeavesTheContextUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void nonBearerAuthorizationHeaderIsIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void existingAuthenticationIsNotOverwritten() throws Exception {
        // Simulates a stronger authentication already set earlier in the chain -
        // JwtAuthFilter must not clobber it even if a Bearer header is also present.
        var existing = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "preexisting", null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);

        String token = jwtService.issue("alice", "ADMIN");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }
}
