package com.shoplocker.fssai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;

@Configuration
public class AwsTextractConfig {

    @Value("${aws.region}")
    private String region;

    @Value("${AWS_ACCESS_KEY_ID:}")
    private String accessKeyId;

    @Value("${AWS_SECRET_ACCESS_KEY:}")
    private String secretAccessKey;

    @Bean
    public TextractClient textractClient() {
        // Use explicit credentials from .env if available, otherwise fall back
        // to default credential chain (EC2 instance profile, ~/.aws/credentials, etc.)
        if (!accessKeyId.isEmpty() && !secretAccessKey.isEmpty()) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
            return TextractClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build();
        }

        return TextractClient.builder()
                .region(Region.of(region))
                .build();
    }
}
