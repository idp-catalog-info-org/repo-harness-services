/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.beans.entities.artifacts;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("io.harness.app.beans.entities.artifacts.FileArtifactMetadata")
public class FileArtifactMetadata implements ArtifactMetadata {
  // Common
  String fileName;
  String url;
  String digest;
  // Storage type is set by CI manager based on the upload step (GCS, S3, ARTIFACTORY, HAR)
  String storageType;

  // Object storage: S3 + GCS (null for Artifactory, HAR)
  String filePath;
  String bucketName;
  String region; // S3 only; null for GCS

  // HAR-specific (null for S3/GCS/Artifactory)
  String registry;
  String packageName;
  String version;
  String packageType;
}
