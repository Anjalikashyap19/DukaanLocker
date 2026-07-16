package com.shoplocker.fssai.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearer-jwt";

    /**
     * Externally-reachable base URL of this instance. Surfaced in the
     * OpenAPI `servers` block so the Swagger UI's "Try it out" base URL
     * resolves to the EC2 public IP/DNS instead of `localhost` (which would
     * silently break every `Try it out` POST when called from outside the
     * EC2 box).
     *
     * <p>Default `http://localhost:8081` keeps tunnel / local-dev setups
     * working unchanged. On EC2, set {@code EXTERNAL_URL=http://&lt;ec2-public-ip&gt;:8081}
     * in {@code .env} (or rely on {@code start.sh}'s auto-detection).</p>
     */
    @Value("${app.external-url:http://localhost:8081}")
    private String externalUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DukaanLocker API")
                        .description("REST API for uploading and managing shop compliance documents "
                                + "(PAN/TAN, GST, FSSAI, trade license, MSME, IEC, etc.). "
                                + "On EC2, the `servers` block below is populated from the EXTERNAL_URL "
                                + "env var (or auto-detected) so the Swagger UI's `Try it out` POSTs hit "
                                + "the externally-reachable hostname, not localhost.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("DukaanLocker")
                                .email("support@dukanlocker.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                // Register the JWT Bearer security scheme so Swagger UI renders
                // the "Authorize" button at the top. Users can paste their JWT
                // token (obtained from POST /api/auth/login) and all subsequent
                // requests will include the `Authorization: Bearer <token>` header.
                // Public endpoints like /api/auth/** are annotated @SecurityRequirements
                // to opt out of this global requirement.
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your JWT token here. Get one by calling POST /api/auth/login with your credentials, or POST /api/auth/register to create a new account.")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                // Single external server. Order matters: Swagger UI uses the FIRST
                // (and in this case only) server as the default `Try it out` base
                // URL, so the externally-reachable URL is exactly what browser-side
                // POSTs hit. We deliberately do NOT also add a `Same-origin relative`
                // Server here because it just adds dropdown noise in the Swagger UI
                // without changing behavior (Springdoc 2.x already uses same-origin
                // resolution when no server list is provided).
                .servers(List.of(
                        new Server().url(externalUrl)
                                .description("External (Swagger UI `Try it out` base)")));
    }
}
