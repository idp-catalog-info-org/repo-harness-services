/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class S3StorageUtil implements CloudStorageUtil {
  public static final String S3_URL_FORMAT = "https://%s.s3.%s.amazonaws.com/";
  private static final String PATH_SEPARATOR = "/";
  private static final Duration PRESIGN_DURATION = Duration.ofDays(7);
  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
  private final String region;

  public S3StorageUtil(String region) {
    this.region = region;
    DefaultCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
    Region awsRegion = Region.of(region);
    this.s3Client = S3Client.builder().region(awsRegion).credentialsProvider(credentialsProvider).build();
    this.s3Presigner = S3Presigner.builder().region(awsRegion).credentialsProvider(credentialsProvider).build();
  }

  S3StorageUtil(S3Client s3Client, S3Presigner s3Presigner, String region) {
    this.s3Client = s3Client;
    this.s3Presigner = s3Presigner;
    this.region = region;
  }

  @Override
  public String uploadFile(String bucketName, String filePath, String fileName, InputStream fileContent) {
    String key = filePath + PATH_SEPARATOR + fileName;
    try {
      byte[] bytes = fileContent.readAllBytes();
      PutObjectRequest putRequest = PutObjectRequest.builder().bucket(bucketName).key(key).build();
      s3Client.putObject(putRequest, RequestBody.fromBytes(bytes));
      log.info("File uploaded to S3: {}", key);
      return getS3BaseUrl(bucketName) + key;
    } catch (IOException e) {
      String errorMessage = "Could not upload file to S3: " + bucketName + PATH_SEPARATOR + key;
      log.error(errorMessage);
      throw new UnexpectedException(errorMessage, e);
    } finally {
      if (fileContent != null) {
        try {
          fileContent.close();
        } catch (IOException e) {
          throw new UnexpectedException("Could not close file stream", e);
        }
      }
    }
  }

  @Override
  public void deleteFile(String fileUrl) {
    try {
      URI uri = new URI(fileUrl);
      String bucketName = extractBucketFromHost(uri.getHost());
      String key = uri.getPath().substring(1);

      DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder().bucket(bucketName).key(key).build();
      s3Client.deleteObject(deleteRequest);
      log.info("File deleted from S3: bucket={}, key={}", bucketName, key);
    } catch (URISyntaxException e) {
      throw new UnexpectedException("Invalid S3 URL: " + fileUrl, e);
    }
  }

  @Override
  public byte[] readFile(String fileUrl) {
    try {
      URI uri = new URI(fileUrl);
      String bucketName = extractBucketFromHost(uri.getHost());
      String key = uri.getPath().substring(1);

      GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();
      try (var responseStream = s3Client.getObject(getRequest)) {
        return responseStream.readAllBytes();
      }
    } catch (URISyntaxException e) {
      throw new UnexpectedException("Invalid S3 URL: " + fileUrl, e);
    } catch (IOException e) {
      throw new UnexpectedException("Could not read file from S3: " + fileUrl, e);
    }
  }

  @Override
  public String getReadableUrl(String storageUrl) {
    if (storageUrl == null || !storageUrl.contains(".s3.")) {
      return storageUrl;
    }
    try {
      URI uri = new URI(storageUrl);
      String bucketName = extractBucketFromHost(uri.getHost());
      String key = uri.getPath().substring(1);

      GetObjectPresignRequest presignRequest =
          GetObjectPresignRequest.builder()
              .signatureDuration(PRESIGN_DURATION)
              .getObjectRequest(GetObjectRequest.builder().bucket(bucketName).key(key).build())
              .build();
      PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
      return presigned.url().toString();
    } catch (URISyntaxException e) {
      log.warn("Invalid S3 URL for presigning, returning as-is: {}", storageUrl, e);
      return storageUrl;
    }
  }

  @Override
  public List<String> fetchImageUrls(String bucketName, String path) {
    List<String> imageUrls = new ArrayList<>();
    try {
      String continuationToken = null;
      do {
        ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder().bucket(bucketName).prefix(path);
        if (continuationToken != null) {
          requestBuilder.continuationToken(continuationToken);
        }
        ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());
        for (S3Object s3Object : response.contents()) {
          String rawUrl = getS3BaseUrl(bucketName) + s3Object.key();
          imageUrls.add(getReadableUrl(rawUrl));
        }
        continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;
      } while (continuationToken != null);
    } catch (Exception e) {
      String errorMessage = "Could not fetch images from S3: " + bucketName + "/" + path;
      throw new UnexpectedException(errorMessage, e);
    }
    return imageUrls;
  }

  private String getS3BaseUrl(String bucketName) {
    return String.format(S3_URL_FORMAT, bucketName, region);
  }

  /**
   * Extracts bucket name from S3 virtual-hosted-style hostname.
   * E.g. "my-bucket.s3.us-west-2.amazonaws.com" -> "my-bucket"
   */
  private String extractBucketFromHost(String host) {
    int idx = host.indexOf(".s3.");
    if (idx > 0) {
      return host.substring(0, idx);
    }
    throw new UnexpectedException("Cannot extract bucket name from S3 URL host: " + host);
  }
}
