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
import io.harness.polling.contracts.EcrPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
public class EcrPollingItemGenerator implements PollingItemGenerator {
  @Inject BuildTriggerHelper buildTriggerHelper;

  @Override
  public PollingItem generatePollingItem(
      BuildTriggerOpsData buildTriggerOpsData, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();
    PollingItem.Builder builder =
        getBaseInitializedPollingItem(ngTriggerEntity, buildTriggerOpsData, scopeInfo, isParentIdQueryingEnabled);
    String connectorRefKey;
    String registryIdKey;
    String imagePath;
    if (HarnessYamlVersion.isV1(ngTriggerEntity.getHarnessVersion())) {
      imagePath = buildTriggerHelper
                      .getImageAndTagFromLocation(
                          buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, Constants.LOCATION))
                      .getLeft();
      connectorRefKey = Constants.CONNECTOR;
      registryIdKey = Constants.REGISTRY;
    } else {
      connectorRefKey = "spec.connectorRef";
      imagePath = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.imagePath");
      registryIdKey = "spec.registryId";
    }
    String region = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.region");
    String connectorRef = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, connectorRefKey);
    String registryId = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, registryIdKey);
    return builder
        .setPollingPayloadData(
            PollingPayloadData.newBuilder()
                .setConnectorRef(connectorRef)
                .setType(Type.ECR)
                .setEcrPayload(
                    EcrPayload.newBuilder().setRegion(region).setImagePath(imagePath).setRegistryId(registryId).build())
                .build())
        .build();
  }
}
