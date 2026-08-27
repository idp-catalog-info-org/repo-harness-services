/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.pipeline;

import static io.harness.pms.utils.NGPipelineSettingsConstant.MAX_PIPELINE_TIMEOUT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.steps.stepinfo.GitCloneStepInfoV1;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.utils.PipelineV1InputVarsUtils;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.children.ChildrenPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.utils.SdkTimeoutObtainmentUtils;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.common.pipeline.PipelineSetupStep;
import io.harness.steps.common.pipeline.PipelineSetupStepParameters;
import io.harness.utils.CommonPlanCreatorUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelinePlanCreatorV1 extends ChildrenPlanCreator<YamlField> {
  @Inject KryoSerializer kryoSerializer;
  @Override
  public String getStartingNodeId(YamlField field) {
    return field.getUuid();
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, YamlField config) {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    Map<String, YamlField> dependencies = new HashMap<>();
    YamlField specNode = config.getNode().getField(YAMLFieldNameConstants.STAGES);
    if (specNode == null) {
      specNode = config.getNode().getField(YAMLFieldNameConstants.JOBS);
    }
    if (specNode.getNode() == null) {
      return responseMap;
    }
    dependencies.put(specNode.getNode().getUuid(), specNode);
    responseMap.put(specNode.getNode().getUuid(),
        PlanCreationResponse.builder()
            .dependencies(DependenciesUtils.toDependenciesProto(dependencies)
                              .toBuilder()
                              .putDependencyMetadata(specNode.getUuid(), buildDependencyForSpec(config))
                              .build())
            .build());
    return responseMap;
  }

  Dependency buildDependencyForSpec(YamlField config) {
    Dependency.Builder dependencyBuilder = Dependency.newBuilder();
    HarnessStruct parentInfo = buildParentInfo(config);
    if (parentInfo != null) {
      dependencyBuilder.setParentInfo(parentInfo);
    }
    HarnessStruct.Builder nodeMetadataBuilder = HarnessStruct.newBuilder().putData(
        PlanCreatorConstants.SET_STARTING_NODE_ID, HarnessValue.newBuilder().setBoolValue(true).build());
    // repo is now part of clone, so we only set clone metadata
    setCloneMetadata(config, nodeMetadataBuilder);
    setPermissionsMetadata(config, nodeMetadataBuilder);
    return dependencyBuilder.setNodeMetadata(nodeMetadataBuilder.build()).build();
  }

  private void setCloneMetadata(YamlField config, HarnessStruct.Builder nodeMetadataBuilder) {
    GitCloneStepInfoV1 gitCloneStepInfoV1 = null;
    YamlField cloneField = config.getNode().getField(YAMLFieldNameConstants.CLONE);
    if (cloneField != null) {
      try {
        gitCloneStepInfoV1 = YamlUtils.read(cloneField.getNode().toString(), GitCloneStepInfoV1.class);
      } catch (IOException ex) {
        throw new InvalidRequestException("Invalid clone yaml", ex);
      }
    }
    if (gitCloneStepInfoV1 != null) {
      nodeMetadataBuilder.putData(YAMLFieldNameConstants.CLONE,
          HarnessValue.newBuilder()
              .setBytesValue(ByteString.copyFrom(kryoSerializer.asBytes(gitCloneStepInfoV1)))
              .build());
    }
  }

  private void setPermissionsMetadata(YamlField config, HarnessStruct.Builder nodeMetadataBuilder) {
    YamlField permissionsField = config.getNode().getField(YAMLFieldNameConstants.PERMISSIONS);
    if (permissionsField == null) {
      return;
    }
    try {
      Map<String, String> permissions = YamlUtils.read(permissionsField.getNode().toString(), Map.class);
      if (permissions != null) {
        nodeMetadataBuilder.putData(YAMLFieldNameConstants.PERMISSIONS,
            HarnessValue.newBuilder().setBytesValue(ByteString.copyFrom(kryoSerializer.asBytes(permissions))).build());
      }
    } catch (IOException ex) {
      throw new InvalidRequestException("Invalid permissions yaml", ex);
    }
  }

  HarnessStruct buildParentInfo(YamlField config) {
    ParameterField<List<TaskSelectorYaml>> delegates = PlanCreatorUtilsV1.getDelegates(config.getNode());
    ParameterField<Map<String, ParameterField<JsonNode>>> envVars = getPipelineEnvVars(config);
    HarnessStruct.Builder responseBuilder = HarnessStruct.newBuilder();
    if (ParameterField.isNotNull(delegates)) {
      responseBuilder.putData(PlanCreatorConstants.PIPELINE_DELEGATES,
          HarnessValue.newBuilder()
              .setBytesValue(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(delegates)))
              .build());
    }
    if (ParameterField.isNotNull(envVars)) {
      responseBuilder.putData(PlanCreatorConstants.PIPELINE_ENV,
          HarnessValue.newBuilder()
              .setBytesValue(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(envVars)))
              .build());
    }
    return responseBuilder.build();
  }

  ParameterField<Map<String, ParameterField<JsonNode>>> getPipelineEnvVars(YamlField field) {
    YamlField envField = field.getNode().getField(YAMLFieldNameConstants.ENV);
    try {
      return YamlUtils.read(
          envField.getNode().toString(), new TypeReference<ParameterField<Map<String, ParameterField<JsonNode>>>>() {});
    } catch (Exception ex) {
      return null;
    }
  }

  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx, YamlField config, List<String> childrenNodeIds) {
    PlanNodeBuilder planNodeBuilder =
        PlanNode.builder()
            .uuid(config.getUuid())
            .identifier(YAMLFieldNameConstants.PIPELINE)
            .stepType(PipelineSetupStep.STEP_TYPE)
            .group(StepOutcomeGroup.PIPELINE.name())
            .name(config.getName())
            .skipUnresolvedExpressionsCheck(true)
            .stepParameters(getStepParametersV1(ctx, config, childrenNodeIds.get(0)))
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                    .build())
            .skipExpressionChain(false);
    ParameterField<Timeout> timeout =
        SdkTimeoutObtainmentUtils.getTimeout(getTimeout(config), ctx.getTimeoutDuration(MAX_PIPELINE_TIMEOUT.getName()),
            ctx.getFeatureFlagValue(FeatureName.CDS_DISABLE_MAX_TIMEOUT_CONFIG.toString()));
    planNodeBuilder = setStageTimeoutObtainment(timeout, planNodeBuilder);
    return planNodeBuilder.build();
  }

  private ParameterField<Timeout> getTimeout(YamlField field) {
    ParameterField<Timeout> timeoutParameterField = null;
    if (field.getNode().getField(YAMLFieldNameConstants.TIMEOUT) != null) {
      try {
        timeoutParameterField =
            YamlUtils.read(field.getNode().getField(YAMLFieldNameConstants.TIMEOUT).getNode().toString(),
                new TypeReference<ParameterField<Timeout>>() {});
      } catch (IOException e) {
        return null;
      }
    }
    return timeoutParameterField;
  }

  private String getName(YamlField config) {
    YamlNode pipelineNode = config.getNode();
    if (pipelineNode != null && pipelineNode.getField(YAMLFieldNameConstants.NAME) != null) {
      return pipelineNode.getField(YAMLFieldNameConstants.NAME).getNode().getCurrJsonNode().asText();
    }
    return null;
  }

  private ParameterField<String> getDescription(YamlField field) {
    ParameterField<String> descriptionParameterField = null;
    if (field.getNode().getField(YAMLFieldNameConstants.DESCRIPTION) != null) {
      try {
        descriptionParameterField =
            YamlUtils.read(field.getNode().getField(YAMLFieldNameConstants.DESCRIPTION).getNode().toString(),
                new TypeReference<ParameterField<String>>() {});
      } catch (IOException e) {
        return null;
      }
    }
    return descriptionParameterField;
  }

  private String getIdentifier(YamlField config) {
    YamlNode pipelineNode = config.getNode();
    if (pipelineNode != null && pipelineNode.getField(YAMLFieldNameConstants.ID) != null) {
      return pipelineNode.getField(YAMLFieldNameConstants.ID).getNode().getCurrJsonNode().asText();
    }
    return null;
  }

  @Override
  public YamlField getFieldObject(YamlField field) {
    return field;
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V1);
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YAMLFieldNameConstants.PIPELINE, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  public PipelineSetupStepParameters getStepParametersV1(
      PlanCreationContext ctx, YamlField config, String childNodeID) {
    ParameterField<List<TaskSelectorYaml>> delegates = PlanCreatorUtilsV1.getDelegates(config.getNode());
    Map<String, Object> variables = PipelineV1InputVarsUtils.getInputsNodeAsVariables(ctx.getCurrentField().getNode());
    CommonPlanCreatorUtils.validateInputVariablesV1(variables,
        "Execution Input is not allowed for pipeline variables as it is similar to making it a runtime input");
    ParameterField<Timeout> timeout = getTimeout(config);
    PipelineSetupStepParameters stepParameters =
        PipelineSetupStepParameters.newBuilder()
            .childNodeID(childNodeID)
            .executionId(ctx.getExecutionUuid())
            .delegateSelectors(delegates)
            .name(getName(config))
            .identifier(getIdentifier(config))
            .executionId(ctx.getExecutionUuid())
            .description(getDescription(config))
            .timeout(ParameterField.isBlank(timeout)
                    ? null
                    : ParameterField.createValueField(timeout.getValue().getTimeoutString()))
            .build();
    stepParameters.setVariables(ParameterField.createValueField(variables));
    return stepParameters;
  }
}
