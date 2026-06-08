package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.auth.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class DataOperationRecognitionBypassFilter extends OncePerRequestFilter {
    private static final Pattern RECOGNIZE_PATH = Pattern.compile("^/api/v1/data-ops/assets/\\d+/recognize$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isDataOperationAssetRecognition(request)) {
            Authentication current = SecurityContextHolder.getContext().getAuthentication();
            if (current == null || !current.isAuthenticated()) {
                UserPrincipal principal = new UserPrincipal(0L, "data-operation-recognition", "数据页识别", "DATA", "DATA_OPS");
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
                );
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isDataOperationAssetRecognition(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && RECOGNIZE_PATH.matcher(request.getRequestURI()).matches();
    }
}
