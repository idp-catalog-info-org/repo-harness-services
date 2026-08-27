/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.polling.contracts.AcrPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(CDP)
public class AcrPollingItemGenerator implements PollingItemGenerator {
  @Inject BuildTriggerHelper buildTriggerHelper;

  @Override
  public PollingItem generatePollingItem(
      BuildTriggerOpsData buildTriggerOpsData, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();
    PollingItem.Builder builder =
        getBaseInitializedPollingItem(ngTriggerEntity, buildTriggerOpsData, scopeInfo, isParentIdQueryingEnabled);
    String connectorKey;
    String repoKey;
    String subscriptionKey;

    if (HarnessYamlVersion.isV1(ngTriggerEntity.getHarnessVersion())) {
      connectorKey = Constants.CONNECTOR;
      repoKey = Constants.REPO;
      subscriptionKey = Constants.SUBSCRIPTION;
    } else {
      connectorKey = "spec.connectorRef";
      repoKey = "spec.repository";
      subscriptionKey = "spec.subscriptionId";
    }
    String connectorRef = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, connectorKey);
    String repository = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, repoKey);
    String subscriptionId = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, subscriptionKey);
    String registry = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, Constants.REGISTRY);
    return builder
        .setPollingPayloadData(PollingPayloadData.newBuilder()
                                   .setConnectorRef(connectorRef)
                                   .setType(Type.ACR)
                                   .setAcrPayload(AcrPayload.newBuilder()
                                                      .setSubscriptionId(subscriptionId)
                                                      .setRegistry(registry)
                                                      .setRepository(repository)
                                                      .build())
                                   .build())
        .build();
  }
}
