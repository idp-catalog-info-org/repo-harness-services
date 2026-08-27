/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.common;

import static io.harness.idp.common.Constants.GCS_PUBLIC_URL_API_PATH;
import static io.harness.idp.common.Constants.GCS_STORAGE_API_PATH;
import static io.harness.idp.common.Constants.IMAGE_PATH_PREFIX;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.google.common.annotations.VisibleForTesting;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class IconUtils {
  public String getIconPath(String accountIdentifier, IconUploadType iconUploadType, String env) {
    return IMAGE_PATH_PREFIX + FileUtils.PATH_SEPARATOR + env + FileUtils.PATH_SEPARATOR + accountIdentifier
        + FileUtils.PATH_SEPARATOR + iconUploadType.name();
  }

  private static final String STORAGE_TYPE_S3 = "S3";

  public String getUrlToReturnFromGcsURl(String gcsURl, boolean cdnEnabled, String bucketName, String cdnDNS) {
    return cdnEnabled ? gcsURl.replace(GCS_STORAGE_API_PATH + bucketName, cdnDNS)
                      : gcsURl.replace(GCS_STORAGE_API_PATH, GCS_PUBLIC_URL_API_PATH);
  }

  public String getGcsUrl(String url, boolean cdnEnabled, String bucketName, String cdnDNS) {
    return cdnEnabled ? url.replace(cdnDNS, GCS_STORAGE_API_PATH + bucketName)
                      : url.replace(GCS_PUBLIC_URL_API_PATH, GCS_STORAGE_API_PATH);
  }

  /**
   * Returns the public/CDN URL from the raw storage URL, dispatching based on storageType.
   * For S3: generates a presigned URL via cloudStorageUtil (or CDN URL if CDN enabled).
   * For GCS: delegates to the existing getUrlToReturnFromGcsURl logic.
   */
  public String getPublicUrl(String storageUrl, boolean cdnEnabled, String bucketName, String cdnDNS,
      String storageType, CloudStorageUtil cloudStorageUtil) {
    if (STORAGE_TYPE_S3.equalsIgnoreCase(storageType)) {
      if (cdnEnabled) {
        return storageUrl.replaceFirst("https://[^/]+", "https://" + cdnDNS);
      }
      return cloudStorageUtil.getReadableUrl(storageUrl);
    }
    return getUrlToReturnFromGcsURl(storageUrl, cdnEnabled, bucketName, cdnDNS);
  }

  /**
   * Converts a public/CDN URL back to the raw storage URL for deletion/reads.
   * For S3: if CDN enabled, replaces cdnDNS host back to S3 virtual-hosted URL; otherwise returns as-is.
   * For GCS: delegates to the existing getGcsUrl logic.
   */
  public String getStorageUrl(
      String url, boolean cdnEnabled, String bucketName, String cdnDNS, String storageType, String s3Region) {
    if (STORAGE_TYPE_S3.equalsIgnoreCase(storageType)) {
      if (cdnEnabled) {
        String s3Host = bucketName + ".s3." + s3Region + ".amazonaws.com";
        return url.replaceFirst("https://[^/]+", "https://" + s3Host);
      }
      return url;
    }
    return getGcsUrl(url, cdnEnabled, bucketName, cdnDNS);
  }
}
