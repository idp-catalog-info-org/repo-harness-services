/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static io.harness.idp.common.Constants.GCS_STORAGE_API_PATH;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.UnexpectedException;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Slf4j
public class GcpStorageUtil implements CloudStorageUtil {
  public static final String GCS_BASE_URL = "https://storage.cloud.google.com/";
  private static final String PATH_SEPARATOR = "/";
  private final Storage storage;

  public GcpStorageUtil() {
    this.storage = StorageOptions.getDefaultInstance().getService();
  }

  @Override
  public String uploadFile(String bucketName, String filePath, String fileName, InputStream fileContent) {
    try {
      BlobId blobId = BlobId.of(bucketName, filePath + PATH_SEPARATOR + fileName);
      Blob blob = storage.create(BlobInfo.newBuilder(blobId).build(), fileContent.readAllBytes());
      log.info("File uploaded to GCS: {}", blob.getName());
      return GCS_BASE_URL + bucketName + PATH_SEPARATOR + blob.getName();
    } catch (IOException e) {
      String errorMessage =
          "Could not upload file to GCS: " + bucketName + PATH_SEPARATOR + filePath + PATH_SEPARATOR + fileName;
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
  public void deleteFile(String gcsUrl) {
    try {
      URI uri = new URI(gcsUrl);
      String path = uri.getPath().substring(1);
      String[] pathComponents = path.split(PATH_SEPARATOR, 2);

      if (pathComponents.length == 2) {
        String bucketName = pathComponents[0];
        String objectName = pathComponents[1];
        BlobId blobId = BlobId.of(bucketName, objectName);
        boolean deleted = storage.delete(blobId);
        if (deleted) {
          log.info("File deleted from GCS: {}", blobId.getName());
        } else {
          log.warn("File not found or unable to delete from GCS: {}", blobId.getName());
        }
      } else {
        log.warn("File not found or unable to delete from GCS: {}", gcsUrl);
      }
    } catch (URISyntaxException e) {
      throw new UnexpectedException("Invalid GCS URL: " + gcsUrl, e);
    }
  }

  @Override
  public byte[] readFile(String gcsUrl) {
    try {
      URI uri = new URI(gcsUrl);
      String path = uri.getPath().substring(1);
      String[] pathComponents = path.split(PATH_SEPARATOR, 2);

      if (pathComponents.length == 2) {
        String bucketName = pathComponents[0];
        String objectName = pathComponents[1];
        BlobId blobId = BlobId.of(bucketName, objectName);
        Blob blob = storage.get(blobId);
        if (blob == null) {
          throw new UnexpectedException("File not found in GCS: " + gcsUrl);
        }
        return blob.getContent();
      }
      throw new UnexpectedException("Invalid GCS URL format: " + gcsUrl);
    } catch (URISyntaxException e) {
      throw new UnexpectedException("Invalid GCS URL: " + gcsUrl, e);
    }
  }

  @Override
  public String getReadableUrl(String storageUrl) {
    return storageUrl;
  }

  public String uploadFileToGcs(String bucketName, String filePath, String fileName, InputStream fileContent) {
    return uploadFile(bucketName, filePath, fileName, fileContent);
  }

  public void deleteFileFromGcs(String gcsUrl) {
    deleteFile(gcsUrl);
  }

  public byte[] readFileFromGcs(String gcsUrl) {
    return readFile(gcsUrl);
  }

  public byte[] readFileFromGcs(String bucketName, String objectPath) {
    BlobId blobId = BlobId.of(bucketName, objectPath);
    Blob blob = storage.get(blobId);
    if (blob == null) {
      throw new UnexpectedException("File not found in GCS: " + bucketName + "/" + objectPath);
    }
    return blob.getContent();
  }

  @Override
  public List<String> fetchImageUrls(String bucketName, String path) {
    List<String> imageUrls = new ArrayList<>();
    try {
      Bucket bucket = storage.get(bucketName);
      if (bucket == null) {
        throw new IllegalArgumentException("Bucket not found: " + bucketName);
      }

      // List objects under the specified path
      for (Blob blob : bucket.list(Storage.BlobListOption.prefix(path)).iterateAll()) {
        String imageUrl = "https://" + GCS_STORAGE_API_PATH + bucketName + "/" + blob.getName();
        imageUrls.add(imageUrl);
      }
    } catch (Exception e) {
      String errorMessage = "Could not fetch images from GCS: " + bucketName + "/" + path;
      throw new RuntimeException(errorMessage, e);
    }
    return imageUrls;
  }
}
