package com.gridveritas.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MtlsIngestFilter is an opt-in extra requirement (a TLS client cert) on top of
 * the INGEST JWT for /api/v1/attestations specifically - off by default, and
 * must not touch any other route or method even when enabled.
 */
class MtlsIngestFilterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void disabledByDefaultAllowsIngestWithoutAClientCert() throws Exception {
        MtlsIngestFilter filter = new MtlsIngestFilter(false, MAPPER);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/attestations");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void enabledRejectsIngestWithoutAClientCert() throws Exception {
        MtlsIngestFilter filter = new MtlsIngestFilter(true, MAPPER);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/attestations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString()).contains("client_certificate_required");
    }

    @Test
    void enabledAllowsIngestWithAClientCertPresent() throws Exception {
        MtlsIngestFilter filter = new MtlsIngestFilter(true, MAPPER);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/attestations");
        request.setAttribute("jakarta.servlet.request.X509Certificate", new X509Certificate[]{null});
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void enabledOnlyAppliesToAttestationIngestNotOtherRoutes() throws Exception {
        MtlsIngestFilter filter = new MtlsIngestFilter(true, MAPPER);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/verify");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void enabledOnlyAppliesToPostNotGet() throws Exception {
        MtlsIngestFilter filter = new MtlsIngestFilter(true, MAPPER);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/attestations");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
