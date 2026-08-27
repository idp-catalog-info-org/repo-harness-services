/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.polling.contracts.Nexus2RegistryPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDC)
public class Nexus2RegistryPollingItemGenerator implements PollingItemGenerator {
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
    String artifactIdKey;
    String packageNameKey;
    String groupIdKey;
    String connectorRefKey;
    if (HarnessYamlVersion.isV1(ngTriggerEntity.getHarnessVersion())) {
      connectorRefKey = Constants.CONNECTOR;
      repositoryFormatKey = Constants.REPO_FORMAT;
      repositoryKey = Constants.REPO_NAME;
      artifactIdKey = Constants.ARTIFACT;
      groupIdKey = Constants.GROUP_ID;
      packageNameKey = Constants.PKG;
    } else {
      connectorRefKey = "spec.connectorRef";
      repositoryFormatKey = "spec.repositoryFormat";
      artifactIdKey = "spec.artifactId";
      repositoryKey = "spec.repository";
      groupIdKey = "spec.groupId";
      packageNameKey = "spec.packageName";
    }
    repositoryFormat = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, repositoryFormatKey);
    repository = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, repositoryKey);
    String classifier = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.classifier");
    String extension = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.extension");
    Nexus2RegistryPayload.Builder nexus2RegistryPayload = Nexus2RegistryPayload.newBuilder();
    nexus2RegistryPayload.setRepositoryFormat(repositoryFormat).setRepository(repository);
    String connectorRef = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, connectorRefKey);
    String artifactId = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, artifactIdKey);
    String groupId = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, groupIdKey);
    String packageName = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, packageNameKey);
    if (repositoryFormat.equalsIgnoreCase("maven")) {
      nexus2RegistryPayload.setArtifactId(artifactId)
          .setGroupId(groupId)
          .setClassifier(classifier)
          .setExtension(extension);
    } else if (repositoryFormat.equalsIgnoreCase("nuget")) {
      nexus2RegistryPayload.setPackageName(packageName);
    } else if (repositoryFormat.equalsIgnoreCase("npm")) {
      nexus2RegistryPayload.setPackageName(packageName);
    } else {
      throw new RuntimeException(String.format("Repository format %s is not supported", repositoryFormat));
    }

    return builder
        .setPollingPayloadData(PollingPayloadData.newBuilder()
                                   .setConnectorRef(connectorRef)
                                   .setType(Type.NEXUS2)
                                   .setNexus2RegistryPayload(nexus2RegistryPayload.build())
                                   .build())
        .build();
  }
}
