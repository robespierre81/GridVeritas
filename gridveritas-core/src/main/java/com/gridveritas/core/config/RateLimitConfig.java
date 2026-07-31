package com.gridveritas.core.config;

import com.gridveritas.core.security.RateLimiter;
import com.gridveritas.core.security.RequestGuardFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Bean
    FilterRegistrationBean<RequestGuardFilter> requestGuardFilter(
            RateLimiter rateLimiter,
            @Value("${gridveritas.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${gridveritas.security.max-body-bytes:262144}") long maxBodyBytes) {

        FilterRegistrationBean<RequestGuardFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new RequestGuardFilter(rateLimiter, maxBodyBytes));
        reg.addUrlPatterns("/*");
        reg.setEnabled(enabled);
        // Run before Spring Security so /auth/token is protected too.
        reg.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 10);
        return reg;
    }
}
