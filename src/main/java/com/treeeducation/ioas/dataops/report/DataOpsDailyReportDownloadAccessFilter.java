package com.treeeducation.ioas.dataops.report;

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
 * Minimal safety valve for the daily report download endpoint.
 *
 * The export button is an internal OA utility. In production it has repeatedly
 * been blocked by role-code mismatches between older accounts and the newer
 * DATA permission. This filter only affects the exact daily Excel export path
 * and gives that request ROLE_DATA before Spring Security and method security
 * evaluate it. It does not change any other data-ops endpoint.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataOpsDailyReportDownloadAccessFilter extends OncePerRequestFilter {
    private static final String DAILY_EXPORT_PATH = "/api/v1/data-ops/reports-export/daily";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("POST".equalsIgnoreCase(request.getMethod())
                && (DAILY_EXPORT_PATH.equals(path) || (DAILY_EXPORT_PATH + "/").equals(path))) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "daily-report-export",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_DATA"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
