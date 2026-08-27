/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipeline.yaml;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.WRAPPER_OBJECT;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.notification.bean.v1.NotificationRules;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.CI)
@Data
@JsonTypeInfo(use = NAME, include = WRAPPER_OBJECT)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonTypeName(YAMLFieldNameConstants.PIPELINE)
public class UnifiedPipelineYaml {
  @JsonProperty(YAMLFieldNameConstants.NOTIFICATIONS) List<NotificationRules> notificationRules;
  @JsonProperty(YAMLFieldNameConstants.ALLOW_STAGE_EXECUTIONS) boolean allowStageExecutions;
  @JsonProperty(YAMLFieldNameConstants.FIXED_INPUTS_ON_RE_RUN) boolean fixedInputsOnRerun;
}
