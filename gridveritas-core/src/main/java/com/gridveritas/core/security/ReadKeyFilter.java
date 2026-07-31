package com.gridveritas.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Grants ROLE_READ when a valid "X-Read-Key" header is present and no stronger
 * (JWT) authentication was already established. Read access only.
 */
@Component
public class ReadKeyFilter extends OncePerRequestFilter {

    private final String readKey;

    public ReadKeyFilter(@Value("${gridveritas.security.read-key}") String readKey) {
        this.readKey = readKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String provided = request.getHeader("X-Read-Key");
            if (provided != null && !readKey.isBlank() && constantTimeEquals(provided, readKey)) {
                var auth = new UsernamePasswordAuthenticationToken(
                        "read-key", null, List.of(new SimpleGrantedAuthority("ROLE_READ")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
