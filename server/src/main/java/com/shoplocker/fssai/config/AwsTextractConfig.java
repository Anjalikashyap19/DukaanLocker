package com.shoplocker.fssai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;

/**
 * Configures the AWS Textract client. Only created when
 * {@code app.ocr.provider=textract} to avoid connection failures
 * when using Tesseract (self-hosted) in production.
 */
@Configuration
@ConditionalOnProperty(name = "app.ocr.provider", havingValue = "textract", matchIfMissing = false)
public class AwsTextractConfig {

    @Value("${aws.region}")
    private String region;

    @Value("${AWS_ACCESS_KEY_ID:}")
    private String accessKeyId;

    @Value("${AWS_SECRET_ACCESS_KEY:}")
    private String secretAccessKey;

    @Value("${aws.endpoint:}")
    private String endpoint;

    @Bean
    public TextractClient textractClient() {
        var builder = TextractClient.builder()
                .region(Region.of(region));

        if (!accessKeyId.isEmpty() && !secretAccessKey.isEmpty()) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
            builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
        }

        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(java.net.URI.create(endpoint));
        }

        return builder.build();
    }
}
