/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.polling.contracts.HelmVersion;
import io.harness.polling.contracts.HttpHelmPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
public class HttpHelmPollingItemGenerator implements PollingItemGenerator {
  @Inject BuildTriggerHelper buildTriggerHelper;

  @Override
  public PollingItem generatePollingItem(
      BuildTriggerOpsData buildTriggerOpsData, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();

    PollingItem.Builder builder =
        getBaseInitializedPollingItem(ngTriggerEntity, buildTriggerOpsData, scopeInfo, isParentIdQueryingEnabled);
    String chartName;
    String helmVersion;
    HelmVersion version;
    String connectorRefKey;

    if (HarnessYamlVersion.isV1(ngTriggerEntity.getHarnessVersion())) {
      connectorRefKey = Constants.HELM_CONNECTOR;
      chartName = buildTriggerHelper
                      .getChartNameAndVersionFromLocation(
                          buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, Constants.CHART))
                      .getLeft();
      helmVersion = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, Constants.HELM_VERSION);
      version = buildTriggerHelper.getHelmVersionFromString(helmVersion);
    } else {
      connectorRefKey = "spec.store.spec.connectorRef";
      chartName = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.chartName");
      helmVersion = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.helmVersion");
      version = HelmVersion.valueOf(helmVersion);
    }
    String connectorRef = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, connectorRefKey);
    return builder
        .setPollingPayloadData(
            PollingPayloadData.newBuilder()
                .setConnectorRef(connectorRef)
                .setType(Type.HTTP_HELM)
                .setHttpHelmPayload(
                    HttpHelmPayload.newBuilder().setChartName(chartName).setHelmVersion(version).build())
                .build())
        .build();
  }
}
