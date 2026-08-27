/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.dto;

import io.harness.ngtriggers.beans.source.NGTriggerType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("BulkTriggerDetail")
@Schema(name = "BulkTriggerDetail",
    description = "This contains details of every trigger toggled for the Bulk Triggers API.")
public class BulkTriggerDetailDTO {
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String pipelineIdentifier;
  String triggerIdentifier;
  NGTriggerType type;
}
