package com.treeeducation.ioas.auth;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnchorRoleRestrictionFilterConfig {
    @Bean
    FilterRegistrationBean<AnchorRoleRestrictionFilter> anchorRoleRestrictionFilterRegistration(AnchorRoleRestrictionFilter filter) {
        FilterRegistrationBean<AnchorRoleRestrictionFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 1);
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
