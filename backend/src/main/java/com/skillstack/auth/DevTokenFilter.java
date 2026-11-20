package com.skillstack.auth;

import com.skillstack.common.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Profile("!prod")
public class DevTokenFilter extends OncePerRequestFilter {
    private final DevTokenService tokens;
    private final ApiErrorWriter errors;

    public DevTokenFilter(DevTokenService tokens, ApiErrorWriter errors) {
        this.tokens = tokens;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                var subject = tokens.verify(authorization.substring(7));
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(subject, null, List.of()));
            } catch (RuntimeException invalid) {
                errors.write(response, request, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "The local session is invalid or expired");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
