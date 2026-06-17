package com.treeeducation.ioas.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/** Restricts anchor accounts to personal-profile APIs only. */
@Component
public class AnchorRoleRestrictionFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof UserPrincipal user && isAnchor(user.role())) {
            String path = request.getRequestURI();
            if (isApiPath(path) && !isAnchorAllowed(path)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"主播账号只能访问个人信息\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAnchor(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) normalized = normalized.substring("ROLE_".length());
        return "ANCHOR".equals(normalized);
    }

    private boolean isApiPath(String path) {
        return path != null && path.startsWith("/api/");
    }

    private boolean isAnchorAllowed(String path) {
        return "/api/v1/auth/me".equals(path)
                || "/api/v1/auth/setup-code/change".equals(path)
                || "/api/v1/profile/me".equals(path);
    }
}
