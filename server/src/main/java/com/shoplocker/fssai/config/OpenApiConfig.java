package com.shoplocker.fssai.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(OpenApiConfig.class);

    private static final String SECURITY_SCHEME_NAME = "bearer-jwt";

    /**
     * Default returned by {@code application.properties}'s
     * {@code app.external-url=${EXTERNAL_URL:http://localhost:8081}} when
     * neither the env var nor IMDS resolves. We never match against this
     * string to decide whether to attempt IMDS — that's dictated purely by
     * whether {@code EXTERNAL_URL} is present in the JVM env (see
     * {@link #resolveExternalUrl}). This constant exists only so the
     * {@code @Value} fallback can reuse it without a magic string.
     */
    private static final String LOCALHOST_DEFAULT = "http://localhost:8081";

    /**
     * Strict dotted-quad + per-octet range check for the public IPv4 body
     * IMDS returns. Guards against an HTML error page masquerading as the
     * answer (IMDSv2-failures, IMDSv1 reply on a non-EC2 host, etc.) AND
     * rejects impossible octet ranges (e.g. 999.0.0.1).
     */
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})$");

    /**
     * Externally-reachable base URL surfaced in the OpenAPI {@code servers}
     * block — i.e. the URL the Swagger UI's "Try it out" POSTs hit.
     *
     * <p>Resolution order (see {@link #resolveExternalUrl}):
     * <ol>
     *   <li>An explicit {@code EXTERNAL_URL} env var (covers HTTPS / custom
     *       domain / CloudFront / ALB DNS) — trusted as-is, even if the
     *       value happens to equal the localhost default.</li>
     *   <li>EC2 Instance Metadata Service (IMDS) auto-detect, with IMDSv2
     *       token flow plus IMDSv1 fallback for legacy instances. Opt out
     *       with {@code SKIP_EC2_AUTODETECT=1}.</li>
     *   <li>{@code http://localhost:8081} (local-dev fallback).</li>
     * </ol></p>
     */
    @Bean
    public OpenAPI customOpenAPI(
            @Value("${app.external-url:" + LOCALHOST_DEFAULT + "}") String externalUrl) {

        String resolvedUrl = resolveExternalUrl(externalUrl);

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
                        new Server().url(resolvedUrl)
                                .description("External (Swagger UI `Try it out` base)")));
    }

    /**
     * Decides which URL ends up in the swagger {@code servers[0]} entry.
     *
     * <p>The IMDS auto-detect is gated on the JVM env, NOT on whether the
     * candidate string equals the localhost default. This means an
     * operator who explicitly sets {@code EXTERNAL_URL=http://localhost:8081}
     * (e.g. a test rig that wants Swagger to mirror dev on a prod-style
     * box) gets exactly what they asked for; we don't silently reach out
     * to IMDS and overwrite.</p>
     */
    private static String resolveExternalUrl(String candidate) {
        String envValue = System.getenv("EXTERNAL_URL");
        if (envValue != null && !envValue.isBlank()) {
            log.info("OpenAPI server URL: {} (from EXTERNAL_URL env var)", candidate);
            return candidate;
        }
        if ("1".equals(System.getenv("SKIP_EC2_AUTODETECT"))) {
            log.info("EC2 IMDS auto-detect skipped via SKIP_EC2_AUTODETECT=1; "
                    + "OpenAPI server URL: {}", candidate);
            return candidate;
        }
        String ec2Ip = fetchEc2PublicIp(2);
        if (ec2Ip != null) {
            String url = "http://" + ec2Ip + ":8081";
            log.info("EC2 IMDS auto-detected OpenAPI server URL: {}", url);
            return url;
        }
        log.info("No EC2 IMDS public-ipv4 reachable (off EC2, IMDS disabled, or "
                + "HttpTokens=required and-token-unobtainable); OpenAPI server URL "
                + "falls back to {} (no EXTERNAL_URL set)", candidate);
        return candidate;
    }

    /**
     * Calls AWS EC2 Instance Metadata Service to retrieve the public IPv4
     * of the host. Handles both IMDSv2 (modern, token-required; default
     * since late 2022 when the AMI-default {@code HttpTokens=required} was
     * introduced) and IMDSv1 (legacy, unauthenticated GET).
     *
     * <p>Returns {@code null} on any failure: timeout, non-200, malformed
     * body, body isn't a valid IPv4, network unreachable, etc. Uses
     * {@link HttpClient} with {@code ProxySelector.of(null)} so any
     * {@code -Dhttp.proxyHost} / {@code HTTPS_PROXY} env var setting
     * can't route the IMDS call through a co-located proxy.</p>
     */
    private static String fetchEc2PublicIp(int timeoutSeconds) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .proxy(java.net.ProxySelector.of(null)) // defeats system proxies
                    .build();

            // Try IMDSv2 first; on legacy hosts IMDSv2 PUT fails (HTTP 405
            // or 404) and we fall back to an unauthenticated GET below.
            String token = fetchImdsToken(client, timeoutSeconds);

            URI ipUri = URI.create("http://169.254.169.254/latest/meta-data/public-ipv4");
            HttpRequest.Builder ipReqBuilder = HttpRequest.newBuilder(ipUri)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET();
            if (token != null) {
                ipReqBuilder.header("X-aws-ec2-metadata-token", token);
            }
            HttpResponse<String> ipRes = client.send(ipReqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (ipRes.statusCode() != 200) {
                log.debug("EC2 IMDS /public-ipv4 returned HTTP {}", ipRes.statusCode());
                return null;
            }
            String ip = ipRes.body().trim();
            return isValidIpv4(ip) ? ip : null;
        } catch (Exception e) {
            log.debug("EC2 IMDS lookup failed: {}", e.toString());
            return null;
        }
    }

    /**
     * IMDSv2 token step: PUT {@code /latest/api/token} with header
     * {@code X-aws-ec2-metadata-token-ttl-seconds: 21600}. Returns the
     * token body, or {@code null} if the host isn't enforcing IMDSv2
     * (in which case the IMDSv1 unauthenticated GET in
     * {@link #fetchEc2PublicIp} is the correct path).
     */
    private static String fetchImdsToken(HttpClient client, int timeoutSeconds) {
        try {
            HttpRequest tokenReq = HttpRequest.newBuilder(
                    URI.create("http://169.254.169.254/latest/api/token"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("X-aws-ec2-metadata-token-ttl-seconds", "21600")
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> tokenRes = client.send(tokenReq,
                    HttpResponse.BodyHandlers.ofString());
            if (tokenRes.statusCode() == 200) {
                log.debug("EC2 IMDSv2 token acquired");
                return tokenRes.body().trim();
            }
            log.debug("EC2 IMDSv2 PUT returned HTTP {} (not on IMDSv2 enforcement)", tokenRes.statusCode());
            return null;
        } catch (Exception e) {
            log.debug("EC2 IMDSv2 token request failed: {}", e.toString());
            return null;
        }
    }

    private static boolean isValidIpv4(String s) {
        if (s == null) {
            return false;
        }
        Matcher m = IPV4_PATTERN.matcher(s);
        if (!m.matches()) {
            return false;
        }
        for (int i = 1; i <= 4; i++) {
            if (Integer.parseInt(m.group(i)) > 255) {
                return false;
            }
        }
        return true;
    }
}
