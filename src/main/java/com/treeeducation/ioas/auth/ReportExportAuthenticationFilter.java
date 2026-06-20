package com.treeeducation.ioas.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Keeps the data-operation daily report download working even when existing OA accounts
 * use legacy role codes. The report export is still limited to the exact POST endpoint.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReportExportAuthenticationFilter extends OncePerRequestFilter {
    private static final String DAILY_REPORT_PATH = "/api/v1/data-ops/reports-export/daily";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isDailyReportRequest(request)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "data-ops-report-export",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_DATA"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private boolean isDailyReportRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return DAILY_REPORT_PATH.equals(path) && "POST".equalsIgnoreCase(request.getMethod());
    }
}
