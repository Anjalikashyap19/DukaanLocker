package com.shoplocker.fssai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Bridges the bare {@code /swagger-ui} and {@code /swagger-ui/} URLs to
 * {@code /swagger-ui/index.html}.
 *
 * <p>Springdoc-openapi 2.x's auto-configured static-resource handler maps
 * every {@code /swagger-ui/<file>} path to the bundled swagger-ui webjar
 * (e.g. {@code /swagger-ui/index.html}, {@code /swagger-ui/swagger-ui-bundle.js},
 * etc.) but does NOT emit a 302 for the bare directory URL. Combined with
 * Spring's trailing-slash normalization, browsers that open
 * {@code http://host:8081/swagger-ui} get redirected to
 * {@code /swagger-ui/} which serves the Whitelabel 404 — even though
 * {@code /swagger-ui/index.html} would gladly serve the Swagger UI HTML.</p>
 *
 * <p>This config wires a single redirect view-controller so any URL ending
 * in {@code /swagger-ui} or {@code /swagger-ui/} lands on
 * {@code /swagger-ui/index.html} with a 302 the browser follows
 * automatically.</p>
 */
@Configuration
public class SwaggerRedirectConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/swagger-ui", "/swagger-ui/index.html");
        registry.addRedirectViewController("/swagger-ui/", "/swagger-ui/index.html");
    }
}
