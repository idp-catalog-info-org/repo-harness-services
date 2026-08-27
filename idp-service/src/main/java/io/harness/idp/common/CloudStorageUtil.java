/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.io.InputStream;
import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public interface CloudStorageUtil {
  String uploadFile(String bucketName, String filePath, String fileName, InputStream fileContent);

  void deleteFile(String fileUrl);

  byte[] readFile(String fileUrl);

  List<String> fetchImageUrls(String bucketName, String path);

  /**
   * Converts a storage URL into a URL readable by an external client.
   * For GCS (public buckets), returns the URL unchanged.
   * For S3 (private buckets), returns a time-limited presigned GET URL.
   */
  String getReadableUrl(String storageUrl);
}
