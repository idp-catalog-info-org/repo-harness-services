/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.polling.contracts.Nexus3RegistryPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(CDP)
public class Nexus3PollingItemGenerator implements PollingItemGenerator {
  @Inject BuildTriggerHelper buildTriggerHelper;

  @Override
  public PollingItem generatePollingItem(
      BuildTriggerOpsData buildTriggerOpsData, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();
    PollingItem.Builder builder =
        getBaseInitializedPollingItem(ngTriggerEntity, buildTriggerOpsData, scopeInfo, isParentIdQueryingEnabled);
    String repository;
    String repositoryKey;
    String repositoryFormat;
    String repositoryFormatKey;
    String repositoryUrl;
    String repositoryUrlKey;
    String connectorRefKey;
    String artifactIdKey;
    String groupIdKey;
    String packageNameKey;
    String repositoryPortKey;
    String artifactPathKey;

    if (HarnessYamlVersion.isV1(ngTriggerEntity.getHarnessVersion())) {
      connectorRefKey = Constants.CONNECTOR;
      artifactIdKey = Constants.ARTIFACT;
      groupIdKey = Constants.GROUP_ID;
      packageNameKey = Constants.PKG;
      repositoryPortKey = Constants.PORT;
      artifactPathKey = Constants.PATH;
      repositoryKey = Constants.REPO_NAME;
      repositoryFormatKey = Constants.REPO_FORMAT;
      repositoryUrlKey = Constants.REPO_URL;
    } else {
      connectorRefKey = "spec.connectorRef";
      repositoryKey = "spec.repository";
      artifactPathKey = "spec.artifactPath";
      repositoryUrlKey = "spec.repositoryUrl";
      artifactIdKey = "spec.artifactId";
      repositoryFormatKey = "spec.repositoryFormat";
      groupIdKey = "spec.groupId";
      packageNameKey = "spec.packageName";
      repositoryPortKey = "spec.repositoryPort";
    }
    repositoryUrl = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, repositoryUrlKey);
    repositoryFormat = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, repositoryFormatKey);
    repository = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, repositoryKey);
    String group = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.group");
    String classifier = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.classifier");
    String extension = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.extension");
    Nexus3RegistryPayload.Builder nexus3RegistryPayload = Nexus3RegistryPayload.newBuilder();
    nexus3RegistryPayload.setRepositoryFormat(repositoryFormat).setRepository(repository);
    String connectorRef = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, connectorRefKey);
    String artifactPath = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, artifactPathKey);
    String artifactId = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, artifactIdKey);
    String groupId = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, groupIdKey);
    String packageName = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, packageNameKey);
    String repositoryPort = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, repositoryPortKey);
    if ("maven".equalsIgnoreCase(repositoryFormat)) {
      nexus3RegistryPayload.setArtifactId(artifactId)
          .setGroupId(groupId)
          .setClassifier(classifier)
          .setExtension(extension);
    } else if ("docker".equalsIgnoreCase(repositoryFormat)) {
      nexus3RegistryPayload.setRepositoryUrl(repositoryUrl)
          .setRepositoryPort(repositoryPort)
          .setArtifactPath(artifactPath);
    } else if ("nuget".equalsIgnoreCase(repositoryFormat)) {
      nexus3RegistryPayload.setPackageName(packageName);
    } else if ("npm".equalsIgnoreCase(repositoryFormat)) {
      nexus3RegistryPayload.setPackageName(packageName);
    } else if ("raw".equalsIgnoreCase(repositoryFormat)) {
      nexus3RegistryPayload.setGroup(group);
    } else {
      throw new RuntimeException(String.format("Repository format %s is not supported", repositoryFormat));
    }

    return builder
        .setPollingPayloadData(PollingPayloadData.newBuilder()
                                   .setConnectorRef(connectorRef)
                                   .setType(Type.NEXUS3)
                                   .setNexus3RegistryPayload(nexus3RegistryPayload.build())
                                   .build())
        .build();
  }
}
