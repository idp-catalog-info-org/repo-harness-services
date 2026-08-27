/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.polling.contracts.GithubPackagesPollingPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(CDC)
public class GithubPackagesPollingItemGenerator implements PollingItemGenerator {
  @Inject BuildTriggerHelper buildTriggerHelper;
  @Override
  public PollingItem generatePollingItem(
      BuildTriggerOpsData buildTriggerOpsData, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();
    PollingItem.Builder builder =
        getBaseInitializedPollingItem(ngTriggerEntity, buildTriggerOpsData, scopeInfo, isParentIdQueryingEnabled);
    String connectorRefKey;
    String packageName;
    String packageType;
    String packageNameKey;
    String packageTypeKey;
    if (HarnessYamlVersion.isV1(ngTriggerEntity.getHarnessVersion())) {
      connectorRefKey = Constants.CONNECTOR;
      packageNameKey = Constants.PKG_NAME;
      packageTypeKey = Constants.PKG_TYPE;
    } else {
      connectorRefKey = "spec.connectorRef";
      packageNameKey = "spec.packageName";
      packageTypeKey = "spec.packageType";
    }
    String connectorRef = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, connectorRefKey);
    String org = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.org");
    packageName = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, packageNameKey);
    packageType = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, packageTypeKey);
    return builder
        .setPollingPayloadData(PollingPayloadData.newBuilder()
                                   .setConnectorRef(connectorRef)
                                   .setType(Type.GITHUB_PACKAGES)
                                   .setGithubPackagesPollingPayload(GithubPackagesPollingPayload.newBuilder()
                                                                        .setOrg(org)
                                                                        .setPackageName(packageName)
                                                                        .setPackageType(packageType)
                                                                        .build())
                                   .build())
        .build();
  }
}
