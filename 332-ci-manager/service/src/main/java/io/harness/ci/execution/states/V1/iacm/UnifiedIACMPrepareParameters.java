/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.iacm;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.IACM)
@Data
@Builder
@RecasterAlias("io.harness.ci.states.V1.iacm.UnifiedIACMPrepareExecutionStepParameters")
public class UnifiedIACMPrepareParameters implements StepParameters {
  private ParameterField<String> workspaceId;
  private ParameterField<String> moduleTestId;
  private ParameterField<String> remoteExecutionId;
  private ParameterField<List<String>> playbooks;
  private ParameterField<List<String>> inventories;

  private String webhookConnector;
  private String webhookRepo;
  private String webhookEventType;
  private String webhookLink;
}