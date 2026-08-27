/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.polling.contracts.ArtifactoryRegistryPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;

import software.wings.utils.RepositoryFormat;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import org.apache.logging.log4j.util.Strings;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
public class ArtifactoryRegistryPollingItemGenerator implements PollingItemGenerator {
  @Inject BuildTriggerHelper buildTriggerHelper;

  @Override
  public PollingItem generatePollingItem(
      BuildTriggerOpsData buildTriggerOpsData, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();
    PollingItem.Builder builder =
        getBaseInitializedPollingItem(ngTriggerEntity, buildTriggerOpsData, scopeInfo, isParentIdQueryingEnabled);
    String connectorRefKey;
    String repository;
    String repositoryFormat;
    String artifactDirectoryKey;
    String artifactFilterKey;
    String artifactPathKey;
    String repositoryUrl;
    String repositoryKey;
    String repositoryFormatKey;
    String repositoryUrlKey;
    if (HarnessYamlVersion.isV1(ngTriggerEntity.getHarnessVersion())) {
      connectorRefKey = Constants.CONNECTOR;
      repositoryKey = Constants.REPO_NAME;
      repositoryFormatKey = Constants.REPO_FORMAT;
      repositoryUrlKey = Constants.REPO_URL;
      artifactDirectoryKey = Constants.DIR;
      artifactPathKey = Constants.PATH;
      artifactFilterKey = Constants.FILTER;
    } else {
      connectorRefKey = "spec.connectorRef";
      repositoryKey = "spec.repository";
      artifactDirectoryKey = "spec.artifactDirectory";
      repositoryFormatKey = "spec.repositoryFormat";
      artifactPathKey = "spec.artifactPath";
      repositoryUrlKey = "spec.repositoryUrl";
      artifactFilterKey = "spec.artifactFilter";
    }
    String artifactDirectory =
        buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, artifactDirectoryKey);
    String artifactPath = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, artifactPathKey);
    String artifactFilter = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, artifactFilterKey);
    String connectorRef = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, connectorRefKey);
    repository = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, repositoryKey);
    repositoryFormat = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, repositoryFormatKey);
    repositoryUrl = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, repositoryUrlKey);
    if (RepositoryFormat.generic.toString().equals(repositoryFormat)) {
      artifactPath = Strings.EMPTY;
    }
    return builder
        .setPollingPayloadData(PollingPayloadData.newBuilder()
                                   .setConnectorRef(connectorRef)
                                   .setType(Type.ARTIFACTORY)
                                   .setArtifactoryRegistryPayload(ArtifactoryRegistryPayload.newBuilder()
                                                                      .setArtifactPath(artifactPath)
                                                                      .setRepositoryUrl(repositoryUrl)
                                                                      .setRepository(repository)
                                                                      .setArtifactDirectory(artifactDirectory)
                                                                      .setRepositoryFormat(repositoryFormat)
                                                                      .setArtifactFilter(artifactFilter)
                                                                      .build())
                                   .build())
        .build();
  }
}
