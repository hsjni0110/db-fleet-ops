package com.dbfleetops.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String allowedOrigins;

    public CorsConfig(
            @Value("${db-fleetops.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(parseAllowedOrigins())
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS").allowedHeaders("*")
                .exposedHeaders("Location").allowCredentials(false).maxAge(3600);

        registry.addMapping("/actuator/**").allowedOrigins(parseAllowedOrigins())
                .allowedMethods("GET", "OPTIONS").allowedHeaders("*").allowCredentials(false)
                .maxAge(3600);
    }

    private String[] parseAllowedOrigins() {
        return allowedOrigins.split("\\s*,\\s*");
    }
}
