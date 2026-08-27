/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.unified.service.NGOutcomes.INFRA_IDENTIFIER;
import static io.harness.unified.service.NGOutcomes.INFRA_NAME;
import static io.harness.unified.service.NGOutcomes.INFRA_V0_OUTCOME;
import static io.harness.unified.service.NGOutcomes.INFRA_V0_OUTCOME_YAML;
import static io.harness.unified.service.NGOutcomes.NG_OUTCOMES;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.EnvironmentEntity;
import io.harness.app.beans.entities.InfrastructureEntity;
import io.harness.beans.IdentifierRef;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.cd.beans.outcomes.EnvironmentOutcome;
import io.harness.cd.beans.outcomes.NgInfraYamlOutcome;
import io.harness.cd.mappers.InfrastructureEntityMapper;
import io.harness.ci.cd.service.EnvironmentEntityService;
import io.harness.ci.cd.service.InfrastructureEntityService;
import io.harness.common.utils.CdStepsInputsMergeUtility;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ConnectorInputsMapper;
import io.harness.expression.InputSetMergeHelperV1;
import io.harness.expression.common.ExpressionMode;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.infrastructure.InfrastructureResourceClient;
import io.harness.infrastructure.unified.UnifiedGitEntityInfoResponseDTO;
import io.harness.infrastructure.unified.UnifiedInfraConverterRequestDTO;
import io.harness.infrastructure.unified.UnifiedInfraConverterResponseDTO;
import io.harness.infrastructure.unified.UnifiedInfraConvertorResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.unified.cd.infrastructure.InfraConfig;
import io.harness.unified.cd.infrastructure.InfraInfoConfig;
import io.harness.unified.cd.infrastructure.InfrastructureMetadata;
import io.harness.unified.cd.service.annotations.ObjectFlattener;
import io.harness.unified.error.NgManagerErrorResponseDTO;
import io.harness.unified.service.NGOutcomes;
import io.harness.utils.CDStepsExpressionResolver;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(HarnessTeam.CI)
@Slf4j
public class InfraEntityProcessor {
  @Inject private InfrastructureEntityService infrastructureEntityService;
  @Inject private EnvironmentEntityService environmentEntityService;

  @Inject InfrastructureResourceClient infrastructureResourceClient;
  @Inject private CDStepsExpressionResolver cdStepsExpressionResolver;
  @Inject private ConnectorInputsMapper connectorInputsMapper;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private EnvOutcomeHelper envOutcomeHelper;
  @Inject private RuntimeExpressionConversionHelper expressionConversionHelper;

  public ProcessedInfraResult getGetInfraTaskExecutionMetadata(Ambiance ambiance, String accountId,
      String orgIdentifier, String projectIdentifier, ParameterField<String> serviceRef,
      ParameterField<String> environmentRef, ParameterField<String> infraId,
      ParameterField<Map<String, Object>> infraInputs, ParameterField<String> envBranch,
      @Nullable ParameterField<String> envGroupRef) {
    validateRefsAndThrow(environmentRef, infraId, serviceRef);
    String serviceRefValue = (String) serviceRef.fetchFinalValue();
    String envRefValue = (String) environmentRef.fetchFinalValue();
    String infraIdValue = (String) infraId.fetchFinalValue();
    String envBranchRef = (String) envBranch.fetchFinalValue();
    String envGroupRefValue = ParameterField.isNull(envGroupRef) ? null : envGroupRef.obtainValue();

    IdentifierRef envIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(envRefValue, accountId, orgIdentifier, projectIdentifier);
    IdentifierRef envGroupIdentifierRef = isNotEmpty(envGroupRefValue)
        ? IdentifierRefHelper.getIdentifierRef(envGroupRefValue, accountId, orgIdentifier, projectIdentifier)
        : null;
    String mergedInfraYaml = null;
    UnifiedInfraConvertorResponse infraEntityNgResponse = null;
    EnvironmentOutcome environmentOutcome = null;
    InfrastructureMetadata infraMetadata = null;

    Optional<InfrastructureEntity> infrastructureEntityOp =
        infrastructureEntityService.get(accountId, orgIdentifier, projectIdentifier, envRefValue, infraIdValue);
    if (infrastructureEntityOp.isPresent()) {
      infraMetadata = toInfraMetadata(infrastructureEntityOp.get());
      mergedInfraYaml = getMergedInfraYaml(infraInputs, infrastructureEntityOp.get());
      EnvironmentEntity environmentEntity = getEnvironmentEntityOrThrow(
          accountId, orgIdentifier, projectIdentifier, envIdentifierRef, infrastructureEntityOp.get());
      environmentOutcome = envOutcomeHelper.getEnvironmentOutcome(envRefValue, environmentEntity, envGroupRefValue);
    } else {
      String infraInputsYaml = getInfraInputsYaml(infraInputs);

      // Fetch ngOutcomes sweeping output
      VariablesSweepingOutput ngOutcomes = null;
      OptionalSweepingOutput ngOutcomesSweepingOutput =
          sweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES));
      if (ngOutcomesSweepingOutput.isFound()) {
        ngOutcomes = (VariablesSweepingOutput) ngOutcomesSweepingOutput.getOutput();
      }

      // Extract service and environment outcome YAMLs from ngOutcomes
      String serviceStepOutcomeYaml = null;
      String environmentOutcomeYaml = null;
      if (ngOutcomes != null) {
        Object serviceOutcome = ngOutcomes.get(NGOutcomes.SERVICE.getName());
        if (serviceOutcome instanceof String) {
          serviceStepOutcomeYaml = (String) serviceOutcome;
        }
        Object environmentOutcomeFromNG = ngOutcomes.get(NGOutcomes.ENVIRONMENT.getName());
        if (environmentOutcomeFromNG instanceof String) {
          environmentOutcomeYaml = (String) environmentOutcomeFromNG;
        }
      }

      // Creating context for remote infra
      GitEntityInfo pipelineGitInfo = GitContextHelper.getGitEntityInfo();
      UnifiedGitEntityInfoResponseDTO gitEnvGitInfo =
          getResponse(infrastructureResourceClient.getInfraGitDetails(accountId, orgIdentifier, projectIdentifier,
              envRefValue, envBranchRef, pipelineGitInfo.getBranch(), pipelineGitInfo.getParentEntityRepoName()));

      throwIfNgError(gitEnvGitInfo == null ? null : gitEnvGitInfo.getError(),
          String.format(
              "Failed to fetch git details for infrastructure [%s] in environment [%s], in project [%s], in org [%s]",
              infraIdValue, envRefValue, projectIdentifier, orgIdentifier));

      UnifiedInfraConverterRequestDTO converterRequestDTO = UnifiedInfraConverterRequestDTO.builder()
                                                                .infraInputsYaml(infraInputsYaml)
                                                                .serviceStepOutcomeYaml(serviceStepOutcomeYaml)
                                                                .environmentOutcomeYaml(environmentOutcomeYaml)
                                                                .build();
      infraEntityNgResponse = getResponse(infrastructureResourceClient.convertToUnified(infraIdValue, accountId,
          envIdentifierRef.getOrgIdentifier(), envIdentifierRef.getProjectIdentifier(),
          envIdentifierRef.getIdentifier(), gitEnvGitInfo.getGitEntityInfo().getBranch(),
          gitEnvGitInfo.getGitEntityInfo().getParentEntityRepoName(), converterRequestDTO));

      throwIfNgError(infraEntityNgResponse == null ? null : infraEntityNgResponse.getError(),
          String.format("Failed to convert infrastructure [%s] to unified infrastructure in environment [%s], in "
                  + "project [%s], in org [%s]",
              infraIdValue, envRefValue, projectIdentifier, orgIdentifier));
      if (infraEntityNgResponse != null && infraEntityNgResponse.getResponseDTO() != null
          && isNotEmpty(infraEntityNgResponse.getResponseDTO().getMergedInfrastructureYaml())) {
        infraEntityNgResponse = convertInfraV0OutcomeExpressions(ambiance, infraEntityNgResponse);
        // Updating expressions, if any, since they are coming unresolved from ng
        cdStepsExpressionResolver.updateExpressions(
            ambiance, infraEntityNgResponse, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
        environmentOutcome = envOutcomeHelper.getEnvironmentOutcomeFromNGInfraResponse(
            envRefValue, infraEntityNgResponse, envGroupIdentifierRef);
        infraMetadata = toInfraMetadata(infraEntityNgResponse);
        mergedInfraYaml = infraEntityNgResponse.getResponseDTO().getMergedInfrastructureYaml();
      }
    }

    if (isEmpty(mergedInfraYaml) || infraMetadata == null || environmentOutcome == null) {
      throw new InvalidRequestException(String.format(
          "No infrastructure entity found with identifier [%s] in environment [%s], in project [%s], in org [%s]",
          infraIdValue, envRefValue, projectIdentifier, orgIdentifier));
    }

    InfraConfig infraConfig = InfrastructureEntityMapper.toConfig(mergedInfraYaml);
    saveInfraOutput(ambiance, infraConfig.getInfraInfoConfig());
    if (infraEntityNgResponse != null && infraEntityNgResponse.getResponseDTO() != null
        && isNotEmpty(infraEntityNgResponse.getResponseDTO().getInfraV0OutcomeYaml())) {
      saveInfraV0Outcome(ambiance, infraEntityNgResponse.getResponseDTO());
    }
    // Save infraV0Yaml as sweeping output for template expression resolution
    if (infraEntityNgResponse != null && infraEntityNgResponse.getResponseDTO() != null
        && isNotEmpty(infraEntityNgResponse.getResponseDTO().getInfraV0Yaml())) {
      saveInfraV0YamlSweepingOutput(ambiance, infraEntityNgResponse.getResponseDTO().getInfraV0Yaml());
    }

    return ProcessedInfraResult.builder()
        .serviceRef(serviceRefValue)
        .envRef(envRefValue)
        .infraId(infraIdValue)
        .infraConfig(infraConfig)
        .infraMetadata(infraMetadata)
        .environmentOutcome(environmentOutcome)
        .build();
  }

  private void saveInfraV0Outcome(Ambiance ambiance, UnifiedInfraConverterResponseDTO responseDTO) {
    VariablesSweepingOutput ngOutcomesSweepingOutput = new VariablesSweepingOutput();
    String infraV0OutcomeYaml = responseDTO.getInfraV0OutcomeYaml();
    String infraIdentifier = responseDTO.getIdentifier();
    String infraName = responseDTO.getName();
    ngOutcomesSweepingOutput.put(INFRA_V0_OUTCOME_YAML, infraV0OutcomeYaml);
    if (isNotEmpty(infraIdentifier)) {
      ngOutcomesSweepingOutput.put(INFRA_IDENTIFIER, infraIdentifier);
    }
    if (isNotEmpty(infraName)) {
      ngOutcomesSweepingOutput.put(INFRA_NAME, infraName);
    }
    sweepingOutputService.consumeUpsert(
        ambiance, INFRA_V0_OUTCOME, ngOutcomesSweepingOutput, StepCategory.STAGE.name());
  }

  /**
   * Save infra v0 YAML as sweeping output for template expression resolution.
   * Enables expressions like: ${{ngInfra.spec.connectorRef}}, ${{ngInfra.spec.namespace}}
   *
   * Pattern: mirrors ServiceEntityProcessor#saveServiceOutput
   */
  private void saveInfraV0YamlSweepingOutput(Ambiance ambiance, String infraV0Yaml) {
    try {
      Map<String, Object> infraV0Map = YamlUtils.read(infraV0Yaml, Map.class);
      if (isEmpty(infraV0Map) || !infraV0Map.containsKey("infrastructureDefinition")) {
        log.warn("No infrastructureDefinition found in infraV0Yaml, skipping sweeping output save");
        return;
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> infraDef = (Map<String, Object>) infraV0Map.get("infrastructureDefinition");

      if (infraDef == null || !infraDef.containsKey("spec")) {
        log.warn("No spec found in infrastructureDefinition, skipping sweeping output save");
        return;
      }

      @SuppressWarnings("unchecked") Map<String, Object> spec = (Map<String, Object>) infraDef.get("spec");

      // Build NgInfraYamlOutcome with the spec map
      NgInfraYamlOutcome ngInfraYamlOutcome = NgInfraYamlOutcome.builder().spec(spec).build();

      // Save as sweeping output with key "ngInfra"
      sweepingOutputService.consumeUpsert(ambiance, "ngInfra", ngInfraYamlOutcome, StepCategory.STAGE.name());

    } catch (Exception e) {
      log.warn("Failed to save infra v0 YAML as sweeping output", e);
    }
  }

  private void throwIfNgError(NgManagerErrorResponseDTO error, String contextMessage) {
    if (error == null) {
      return;
    }
    // NG has already composed a Harness-extracted message with its own context; relay it as-is and only fall back
    // to the local context when NG did not populate a message.
    String ngErrorMessage = isNotEmpty(error.getErrorMessage()) ? error.getErrorMessage() : error.getDetailedMessage();
    String message = isNotEmpty(ngErrorMessage) ? ngErrorMessage : contextMessage;
    if (isNotEmpty(error.getErrorCode())) {
      message = String.format("%s [errorCode: %s]", message, error.getErrorCode());
    }
    throw new InvalidRequestException(message);
  }

  public static void validateRefsAndThrow(
      ParameterField<String> environmentRef, ParameterField<String> infraId, ParameterField<String> serviceRef) {
    if (ParameterField.isNull(environmentRef)) {
      throw new InvalidRequestException("Environment reference is missing");
    }
    if (environmentRef.isExpression()) {
      throw new InvalidRequestException(
          "Environment Ref expression has not been resolved, Expression: " + environmentRef.fetchFinalValue());
    }

    if (ParameterField.isNull(infraId)) {
      throw new InvalidRequestException("Infrastructure identifier is missing");
    }
    if (infraId.isExpression()) {
      throw new InvalidRequestException(
          "Infrastructure identifier expression has not been resolved, Expression: " + infraId.fetchFinalValue());
    }

    if (ParameterField.isNull(serviceRef)) {
      throw new InvalidRequestException("Service reference is missing");
    }
    if (serviceRef.isExpression()) {
      throw new InvalidRequestException(
          "Service Ref expression has not been resolved, Expression: " + serviceRef.fetchFinalValue());
    }
  }

  private InfrastructureMetadata toInfraMetadata(UnifiedInfraConvertorResponse infraEntityNgResponse) {
    UnifiedInfraConverterResponseDTO responseDTO = infraEntityNgResponse.getResponseDTO();
    return InfrastructureMetadata.builder()
        .identifier(responseDTO.getIdentifier())
        .name(responseDTO.getName())
        .description(responseDTO.getDescription())
        .tags(responseDTO.getTags())
        .build();
  }

  private InfrastructureMetadata toInfraMetadata(InfrastructureEntity entity) {
    return InfrastructureMetadata.builder()
        .name(entity.getName())
        .identifier(entity.getIdentifier())
        .tags(convertToMap(entity.getTags()))
        .description(entity.getDescription())
        .build();
  }

  private EnvironmentEntity getEnvironmentEntityOrThrow(String accountId, String orgIdentifier,
      String projectIdentifier, IdentifierRef envIdentifierRef, InfrastructureEntity infrastructureEntity) {
    Optional<EnvironmentEntity> environmentEntityOp =
        environmentEntityService.get(accountId, orgIdentifier, projectIdentifier, envIdentifierRef.getIdentifier());
    if (environmentEntityOp.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Parent environment [%s] referred in infrastructure [%s] does not exist",
              envIdentifierRef.getIdentifier(), infrastructureEntity.getIdentifier()));
    }
    return environmentEntityOp.get();
  }

  private String getMergedInfraYaml(
      ParameterField<Map<String, Object>> infraInputs, InfrastructureEntity infrastructure) {
    String mergedInfraYaml = infrastructure.getYaml();
    if (ParameterField.isNotNull(infraInputs) && isNotEmpty(infraInputs.getValue())) {
      mergedInfraYaml = mergeKeyValueInputsToInfraYaml(infraInputs.getValue(), infrastructure);
    }
    return mergedInfraYaml;
  }

  private String mergeKeyValueInputsToInfraYaml(Map<String, Object> infraInputs, InfrastructureEntity infrastructure) {
    JsonNode infraInputsJsonNodes = CdStepsInputsMergeUtility.parseInputsMapToJsonNode(infraInputs);
    return InputSetMergeHelperV1.mergeInputSetIntoEntityYaml(infraInputsJsonNodes, infrastructure.getYaml(),
        connectorInputsMapper, infrastructure.getAccountId(), infrastructure.getOrgIdentifier(),
        infrastructure.getProjectIdentifier(), YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE);
  }

  public static String getInfraInputsYaml(ParameterField<Map<String, Object>> infraInputs) {
    String infraInputsYaml = EMPTY;
    if (ParameterField.isNotNull(infraInputs) && isNotEmpty(infraInputs.obtainValue())) {
      Map<String, Object> infraInputsMap = infraInputs.obtainValue();
      if (infraInputsMap.containsKey("overlay")) {
        infraInputsYaml = YamlPipelineUtils.writeYamlString(infraInputsMap.get("overlay"));
      }
    }
    return infraInputsYaml;
  }

  private void saveInfraOutput(Ambiance ambiance, InfraInfoConfig infraInfoConfig) {
    Map<String, Object> flattenedMap;
    InfraConfigOutput infraConfigOutput = new InfraConfigOutput();

    try {
      flattenedMap = ObjectFlattener.flatten(infraInfoConfig);
      if (isNotEmpty(flattenedMap)) {
        infraConfigOutput.putAll(flattenedMap);
      }
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    if (isNotEmpty(flattenedMap)) {
      sweepingOutputService.consumeUpsert(ambiance, "infraOutput", infraConfigOutput, StepCategory.STAGE.name());
    }
  }

  private UnifiedInfraConvertorResponse convertInfraV0OutcomeExpressions(
      Ambiance ambiance, UnifiedInfraConvertorResponse infraEntityNgResponse) {
    if (!expressionConversionHelper.isExpressionConversionEnabled(ambiance)) {
      return infraEntityNgResponse;
    }
    UnifiedInfraConverterResponseDTO responseDTO = infraEntityNgResponse.getResponseDTO();
    if (responseDTO == null) {
      return infraEntityNgResponse;
    }
    String pipelineYaml = expressionConversionHelper.fetchPipelineYaml(ambiance);
    if (isEmpty(pipelineYaml)) {
      return infraEntityNgResponse;
    }

    String convertedMergedYaml =
        expressionConversionHelper.convertExpressions(responseDTO.getMergedInfrastructureYaml(), pipelineYaml);
    String convertedV0OutcomeYaml =
        expressionConversionHelper.convertExpressions(responseDTO.getInfraV0OutcomeYaml(), pipelineYaml);
    String convertedV0Yaml = expressionConversionHelper.convertExpressions(responseDTO.getInfraV0Yaml(), pipelineYaml);

    if (convertedMergedYaml.equals(responseDTO.getMergedInfrastructureYaml())
        && convertedV0OutcomeYaml.equals(responseDTO.getInfraV0OutcomeYaml())
        && convertedV0Yaml.equals(responseDTO.getInfraV0Yaml())) {
      return infraEntityNgResponse;
    }
    return UnifiedInfraConvertorResponse.builder()
        .responseDTO(UnifiedInfraConverterResponseDTO.builder()
                         .mergedInfrastructureYaml(convertedMergedYaml)
                         .identifier(responseDTO.getIdentifier())
                         .description(responseDTO.getDescription())
                         .name(responseDTO.getName())
                         .tags(responseDTO.getTags())
                         .environmentResponse(responseDTO.getEnvironmentResponse())
                         .scopedServiceRefs(responseDTO.getScopedServiceRefs())
                         .infraV0OutcomeYaml(convertedV0OutcomeYaml)
                         .infraV0Yaml(convertedV0Yaml)
                         .build())
        .build();
  }
}
