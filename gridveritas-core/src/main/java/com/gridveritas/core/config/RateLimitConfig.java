package com.gridveritas.core.config;

import com.gridveritas.core.security.RateLimiter;
import com.gridveritas.core.security.RequestGuardFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class RateLimitConfig {

    @Bean
    FilterRegistrationBean<RequestGuardFilter> requestGuardFilter(
            RateLimiter rateLimiter,
            @Value("${gridveritas.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${gridveritas.security.max-body-bytes:262144}") long maxBodyBytes,
            @Value("${gridveritas.security.trusted-proxies:}") String trustedProxiesCsv) {

        Set<String> trustedProxies = StringUtils.commaDelimitedListToSet(trustedProxiesCsv)
                .stream().map(String::trim).collect(Collectors.toUnmodifiableSet());

        FilterRegistrationBean<RequestGuardFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new RequestGuardFilter(rateLimiter, maxBodyBytes, trustedProxies));
        reg.addUrlPatterns("/*");
        reg.setEnabled(enabled);
        // Run before Spring Security so /auth/token is protected too.
        reg.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 10);
        return reg;
    }
}
