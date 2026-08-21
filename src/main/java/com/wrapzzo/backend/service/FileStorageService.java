package com.wrapzzo.backend.service;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Generic S3-backed object storage -- not tied to any one entity. Company
 * logos are its first consumer; gift/campaign artwork can reuse it later
 * without a redesign. Backed by MinIO locally (same S3 API, endpoint
 * override), real S3 in a real deployment.
 */
@Service
@Slf4j
public class FileStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String publicBaseUrl;

    public FileStorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${s3.bucket}") String bucket,
            @Value("${s3.public-base-url}") String publicBaseUrl) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** A presigned PUT URL the browser can upload directly to -- the backend never touches the file bytes. */
    public String presignUpload(String key, String contentType, Duration expiry) {
        PutObjectRequest objectRequest =
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build();
        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder().signatureDuration(expiry).putObjectRequest(objectRequest).build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        return presigned.url().toString();
    }

    /** Bucket is public-read (see {@link #ensureBucketReady()}), so this is just a direct URL. */
    public String publicUrl(String key) {
        return publicBaseUrl + "/" + key;
    }

    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    /**
     * Idempotent local-dev convenience: creates the bucket and makes it
     * public-read if it doesn't already exist. In a real AWS deployment the
     * bucket and its policy should already be managed via infrastructure,
     * not the app -- so this logs a warning and moves on rather than
     * crashing startup if it lacks permission to do this.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucketReady() {
        try {
            if (!bucketExists()) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("Created S3 bucket '{}'", bucket);
            }
            s3Client.putBucketPolicy(
                    PutBucketPolicyRequest.builder().bucket(bucket).policy(publicReadPolicy(bucket)).build());
        } catch (Exception e) {
            log.warn(
                    "Could not verify/create S3 bucket '{}' or set its public-read policy -- if this is a real AWS"
                            + " deployment, the bucket and policy are expected to be managed via infrastructure, not"
                            + " the app. Error: {}",
                    bucket,
                    e.getMessage());
        }
    }

    private boolean bucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    private static String publicReadPolicy(String bucket) {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": "*",
                      "Action": "s3:GetObject",
                      "Resource": "arn:aws:s3:::%s/*"
                    }
                  ]
                }
                """
                .formatted(bucket);
    }
}
