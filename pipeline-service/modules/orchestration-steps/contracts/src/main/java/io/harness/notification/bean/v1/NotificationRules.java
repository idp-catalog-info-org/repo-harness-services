/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.bean.v1;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.notification.v1.channelDetails.NotificationChannelType;
import io.harness.notification.v1.channelDetails.PmsNotificationChannel;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@JsonIgnoreProperties(ignoreUnknown = true)
@Value
public class NotificationRules {
  String id;
  String name;
  Boolean disabled;
  @JsonProperty(YAMLFieldNameConstants.ON) NotificationEvents notificationEvents;

  @JsonProperty(YAMLFieldNameConstants.USES) NotificationChannelType type;
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "uses", visible = true)
  @JsonProperty(YAMLFieldNameConstants.WITH)
  PmsNotificationChannel spec;
}
