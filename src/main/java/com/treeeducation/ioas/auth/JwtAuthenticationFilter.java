package com.treeeducation.ioas.auth;

import com.treeeducation.ioas.system.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Reads Bearer JWT tokens from requests and installs Spring Security authentication. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                userRepository.findByUsername(claims.getSubject()).ifPresent(user -> {
                    UserPrincipal principal = new UserPrincipal(
                            user.getId(),
                            user.getUsername(),
                            user.getUserName(),
                            user.getRoleCode(),
                            user.getDepartment()
                    );
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
                    );
                });
            } catch (RuntimeException ignored) {
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(String path) {
        return "/".equals(path)
                || "/health".equals(path)
                || "/api/v1/auth/login".equals(path)
                || path.startsWith("/api/v1/public/")
                || "/api/official/leads".equals(path)
                || "/api/official/leads/".equals(path)
                || "/api/files/upload".equals(path)
                || "/swagger-ui.html".equals(path)
                || path.startsWith("/swagger-ui/")
                || "/v3/api-docs".equals(path)
                || path.startsWith("/v3/api-docs/");
    }
}
