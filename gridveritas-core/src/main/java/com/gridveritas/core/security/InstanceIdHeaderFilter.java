package com.gridveritas.core.security;

import com.gridveritas.core.service.InstanceRegistryService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Stamps every response with the serving instance's id (ADR-013) - lets a
 * caller observe (not just assume) that a load balancer is actually
 * distributing traffic across multiple gridveritas-core replicas. Runs first
 * so the header is present on every response, including ones later filters
 * reject (401/429/etc).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InstanceIdHeaderFilter implements Filter {

    private final InstanceRegistryService instanceRegistryService;

    public InstanceIdHeaderFilter(InstanceRegistryService instanceRegistryService) {
        this.instanceRegistryService = instanceRegistryService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.setHeader("X-Instance-Id", instanceRegistryService.getInstanceId());
        }
        chain.doFilter(request, response);
    }
}
