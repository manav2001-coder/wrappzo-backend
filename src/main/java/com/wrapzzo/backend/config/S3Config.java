package com.wrapzzo.backend.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Two separate clients, deliberately: {@code s3Client} is for the backend's
 * own operations (bucket bootstrap, delete) using whatever endpoint the
 * backend container can actually reach. {@code s3Presigner} generates URLs
 * handed straight to the browser, so it must use a browser-reachable
 * endpoint -- these differ under docker-compose (internal "minio" hostname
 * vs. "localhost"), and are identical/unset in a real AWS deployment, since
 * real regional S3 endpoints are already public.
 */
@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(
            @Value("${s3.endpoint}") String endpoint,
            @Value("${s3.region}") String region,
            @Value("${s3.access-key}") String accessKey,
            @Value("${s3.secret-key}") String secretKey) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(
            @Value("${s3.presign-endpoint}") String presignEndpoint,
            @Value("${s3.region}") String region,
            @Value("${s3.access-key}") String accessKey,
            @Value("${s3.secret-key}") String secretKey) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        if (StringUtils.hasText(presignEndpoint)) {
            builder.endpointOverride(URI.create(presignEndpoint))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }
}
