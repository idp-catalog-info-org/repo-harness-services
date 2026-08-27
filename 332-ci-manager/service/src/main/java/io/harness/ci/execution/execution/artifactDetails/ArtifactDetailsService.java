/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution.artifactDetails;

import io.harness.app.beans.entities.artifacts.ArtifactDetails;
import io.harness.app.beans.entities.artifacts.ArtifactDetailsRequestDTO;
import io.harness.beans.execution.PublishedFileArtifact;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.pms.contracts.ambiance.Ambiance;

import java.util.List;

public interface ArtifactDetailsService {
  ArtifactDetails getArtifactDetailsList(String accountIdentifier, ArtifactDetailsRequestDTO artifactDetailsRequestDTO);

  ArtifactDetails saveDockerArtifactDetails(PublishedImageArtifact publishedImageArtifact, Ambiance ambiance);

  ArtifactDetails saveFileArtifactDetails(
      List<PublishedFileArtifact> publishedFileArtifacts, Ambiance ambiance, String storageType);
}
