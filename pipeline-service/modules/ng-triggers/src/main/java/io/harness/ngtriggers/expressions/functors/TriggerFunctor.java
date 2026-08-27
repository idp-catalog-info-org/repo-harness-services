/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions.functors;

import static io.harness.ngtriggers.Constants.CONNECTOR_REF;
import static io.harness.ngtriggers.Constants.EVENT_PAYLOAD;
import static io.harness.ngtriggers.Constants.HEADER;
import static io.harness.ngtriggers.Constants.PAYLOAD;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.PlanExecutionMigrationHelper;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.expression.LateBindingValue;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class TriggerFunctor implements LateBindingValue {
  private final Ambiance ambiance;
  private final PlanExecutionMetadataService planExecutionMetadataService;
  private final PlanExecutionService planExecutionService;

  public TriggerFunctor(Ambiance ambiance, PlanExecutionMetadataService planExecutionMetadataService,
      PlanExecutionService planExecutionService) {
    this.ambiance = ambiance;
    this.planExecutionMetadataService = planExecutionMetadataService;
    this.planExecutionService = planExecutionService;
  }

  @Override
  public Object bind() {
    PlanExecutionMetadata metadata =
        planExecutionMetadataService
            .findByPlanExecutionId(AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId())
            .orElseThrow(()
                             -> new IllegalStateException(
                                 "No Metadata present for planExecution :" + ambiance.getPlanExecutionId()));
    boolean readSwitchEnabled =
        AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
    PlanExecution planExecution = null;
    if (readSwitchEnabled) {
      Optional<PlanExecution> planExecutionOptional =
          planExecutionService.getWithFieldsIncludedOptional(ambiance.getPlanExecutionId(),
              Set.of(PlanExecutionKeys.triggerJsonPayload, PlanExecutionKeys.triggerPayload));
      if (planExecutionOptional.isPresent()) {
        planExecution = planExecutionOptional.get();
      }
    }

    TriggerPayload triggerPayload =
        PlanExecutionMigrationHelper.readTriggerPayloadWithFallBackOnMetadata(metadata, planExecution);
    Map<String, Object> jsonObject = TriggerHelper.buildJsonObjectFromAmbiance(triggerPayload);
    if (nonNull(triggerPayload) && EmptyPredicate.isNotEmpty(triggerPayload.getConnectorRef())) {
      jsonObject.put(CONNECTOR_REF, triggerPayload.getConnectorRef());
    }

    List<HeaderConfig> triggerHeader =
        PlanExecutionMigrationHelper.readTriggerHeaderWithFallBackOnMetadata(metadata, planExecution);
    if (EmptyPredicate.isNotEmpty(triggerHeader)) {
      jsonObject.put(HEADER, new TriggerHeaderBindingMap(triggerHeader));
    }

    String triggerJsonPayload =
        PlanExecutionMigrationHelper.readTriggerJsonPayloadWithFallBackOnMetadata(metadata, planExecution);
    if (isNotBlank(triggerJsonPayload)) {
      jsonObject.put(EVENT_PAYLOAD, triggerJsonPayload);
      // payload
      try {
        jsonObject.put(PAYLOAD, JsonPipelineUtils.read(triggerJsonPayload, HashMap.class));
      } catch (IOException toHashMapEx) {
        try {
          jsonObject.put(PAYLOAD, JsonPipelineUtils.read(triggerJsonPayload, List.class));
        } catch (IOException toListEx) {
          jsonObject.put(PAYLOAD, triggerJsonPayload);
        }
      }
    }
    return jsonObject;
  }
}
