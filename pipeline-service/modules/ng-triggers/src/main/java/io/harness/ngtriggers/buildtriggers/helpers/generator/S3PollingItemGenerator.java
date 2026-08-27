/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.polling.contracts.AmazonS3Payload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
public class S3PollingItemGenerator implements PollingItemGenerator {
  @Inject BuildTriggerHelper buildTriggerHelper;

  @Override
  public PollingItem generatePollingItem(
      BuildTriggerOpsData buildTriggerOpsData, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();
    PollingItem.Builder builder =
        getBaseInitializedPollingItem(ngTriggerEntity, buildTriggerOpsData, scopeInfo, isParentIdQueryingEnabled);
    String connectorRefKey;
    String bucketNameKey;
    String filePathRegexKey;
    if (HarnessYamlVersion.isV1(ngTriggerEntity.getHarnessVersion())) {
      connectorRefKey = Constants.CONNECTOR;
      bucketNameKey = Constants.BUCKET;
      filePathRegexKey = Constants.PATH_REGEX;
    } else {
      connectorRefKey = "spec.connectorRef";
      bucketNameKey = "spec.bucketName";
      filePathRegexKey = "spec.filePathRegex";
    }
    String region = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.region");
    String connectorRef = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, connectorRefKey);
    String bucketName = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, bucketNameKey);
    String filePathRegex = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, filePathRegexKey);
    return builder
        .setPollingPayloadData(PollingPayloadData.newBuilder()
                                   .setConnectorRef(connectorRef)
                                   .setType(Type.AMAZON_S3)
                                   .setAmazonS3Payload(AmazonS3Payload.newBuilder()
                                                           .setBucketName(bucketName)
                                                           .setFilePathRegex(filePathRegex)
                                                           .setRegion(region)
                                                           .build())
                                   .build())
        .build();
  }
}
