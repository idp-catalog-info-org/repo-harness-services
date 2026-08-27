/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.inputset.inputmetadata;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
public class InputsMetadataRequest {
  ModuleType module;
  String fqn;
  String entityType;
  String entityId;
  String inputFormYaml;
  String entityIdentifierKey;
}