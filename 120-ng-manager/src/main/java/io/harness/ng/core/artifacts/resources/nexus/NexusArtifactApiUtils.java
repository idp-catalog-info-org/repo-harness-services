/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.nexus;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusResponseDTO;
import io.harness.cdng.artifact.resources.nexus.service.NexusResourceService;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(HarnessTeam.CDC)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NexusArtifactApiUtils {
  private final NexusResourceService nexusResourceService;
  private final ScopeInfoService scopeInfoService;
  private final ArtifactResourceUtils artifactResourceUtils;

  public NexusResponseDTO getBuildDetails(String accountId, String orgIdentifier, String projectIdentifier,
      String repository, String repositoryPort, String repositoryFormat, String repositoryUrl, String artifactPath,
      String connectorRef, String groupId, String artifactId, String extension, String classifier, String packageName,
      String group) {
    IdentifierRef connectorIdentifierRef =
        IdentifierRefHelper.getConnectorIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorIdentifierRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(connectorIdentifierRef.getAccountIdentifier(),
        connectorIdentifierRef.getOrgIdentifier(), connectorIdentifierRef.getProjectIdentifier());

    return nexusResourceService.getBuildDetails(connectorIdentifierRef, repository, repositoryPort, artifactPath,
        repositoryFormat, repositoryUrl, orgIdentifier, projectIdentifier, groupId, artifactId, extension, classifier,
        packageName, group, scopeInfo);
  }
}
