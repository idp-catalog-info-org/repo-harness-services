/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.ENV_GLOBAL_OVERRIDE;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.ENV_SERVICE_OVERRIDE;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.INFRA_GLOBAL_OVERRIDE;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.INFRA_SERVICE_OVERRIDE;
import static io.harness.yaml.utils.NGVariablesUtils.fetchSecretExpression;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.SecretRefData;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidYamlException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.unified.cd.service.overrides.OverridesConfig;
import io.harness.unified.cd.service.overrides.OverridesWrapperDTO;
import io.harness.unified.cd.service.spec.ServiceConfig;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
@OwnedBy(HarnessTeam.CI)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class OverrideVariablesHelper {
  private static final String VALUE = "value";
  private static final String TYPE = "type";
  private static final String SECRET = "secret";
  private static final String OUTPUT = "output";
  private static final List<ServiceOverridesType> OVERRIDE_IN_REVERSE_PRIORITY =
      List.of(ENV_GLOBAL_OVERRIDE, ENV_SERVICE_OVERRIDE, INFRA_GLOBAL_OVERRIDE, INFRA_SERVICE_OVERRIDE);

  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;

  public void saveVariablesAndCheckAccess(
      Ambiance ambiance, ServiceConfig serviceConfig, Map<ServiceOverridesType, OverridesWrapperDTO> overrides) {
    Map<String, Object> serviceInputsVariables = serviceConfig.getServiceInfoConfig().getInputs();
    Map<String, Object> variables = new HashMap<>();

    Map<String, Object> combinedInputs = getCombinedInputs(overrides, serviceInputsVariables);
    checkForSecretsAccessOrThrow(ambiance, combinedInputs);

    VariablesSweepingOutput outputVariables = getOutputVariables(combinedInputs);

    sweepingOutputService.consumeUpsert(
        ambiance, YAMLFieldNameConstants.SERVICE_VARIABLES, outputVariables, StepCategory.STAGE.name());
    variables.put(OUTPUT, outputVariables);
    VariablesSweepingOutput variablesSweepingOutput = new VariablesSweepingOutput();
    variablesSweepingOutput.putAll(variables);
    sweepingOutputService.consumeUpsert(
        ambiance, YAMLFieldNameConstants.VARIABLES, variablesSweepingOutput, StepCategory.STAGE.name());
  }

  public static Map<String, Object> getCombinedInputs(
      Map<ServiceOverridesType, OverridesWrapperDTO> overrides, Map<String, Object> serviceInputsVariables) {
    Map<String, Object> combinedInputs = new HashMap<>();
    if (isNotEmpty(serviceInputsVariables)) {
      serviceInputsVariables.entrySet()
          .stream()
          .filter(entry -> entry.getValue() instanceof Map)
          .filter(entry -> ((Map<?, ?>) entry.getValue()).containsKey(VALUE))
          .forEach(entry -> combinedInputs.put(entry.getKey(), entry.getValue()));
    }

    if (isEmpty(overrides)) {
      return combinedInputs;
    }

    for (ServiceOverridesType overridesType : OVERRIDE_IN_REVERSE_PRIORITY) {
      if (overrides.containsKey(overridesType)) {
        OverridesConfig overridesConfig = overrides.get(overridesType).getConfig();
        Map<String, Object> overridesInputs = overridesConfig.getOverridesInfoConfig().getInputs();
        if (isNotEmpty(overridesInputs)) {
          overridesInputs.entrySet()
              .stream()
              .filter(entry -> entry.getValue() instanceof Map)
              .filter(entry -> ((Map<?, ?>) entry.getValue()).containsKey(VALUE))
              .forEach(entry -> combinedInputs.put(entry.getKey(), entry.getValue()));
        }
      }
    }
    return combinedInputs;
  }

  public static VariablesSweepingOutput getOutputVariables(Map<String, Object> combinedInputs) {
    VariablesSweepingOutput outputVariables = new VariablesSweepingOutput();

    for (Map.Entry<String, Object> entry : combinedInputs.entrySet()) {
      String key = entry.getKey();
      Object rawValue = entry.getValue();

      if (!(rawValue instanceof Map<?, ?>) ) {
        throw new InvalidYamlException("Incorrect yaml provided for variables");
      }

      Map<?, ?> valueMap = (Map<?, ?>) rawValue;
      Object value = valueMap.get(VALUE);

      if (SECRET.equals(valueMap.get(TYPE))) {
        outputVariables.put(key, fetchSecretExpression((String) value));
      } else {
        outputVariables.put(key, value);
      }
    }

    return outputVariables;
  }

  private void checkForSecretsAccessOrThrow(Ambiance ambiance, Map<String, Object> combinedInputs) {
    List<NGVariable> secretNGVariables =
        combinedInputs.entrySet().stream().filter(this::isSecretVariable).map(this::createSecretNGVariable).toList();
    checkForSecretsAccessOrThrowInternal(ambiance, secretNGVariables);
  }

  private void checkForSecretsAccessOrThrowInternal(Ambiance ambiance, List<NGVariable> serviceVariables) {
    if (EmptyPredicate.isEmpty(serviceVariables)) {
      return;
    }
    List<EntityDetail> entityDetails = new ArrayList<>();

    for (NGVariable ngVariable : serviceVariables) {
      Set<EntityDetailProtoDTO> entityDetailsProto =
          ngVariable == null ? Set.of() : entityReferenceExtractorUtils.extractReferredEntities(ambiance, ngVariable);
      List<EntityDetail> entityDetail =
          entityDetailProtoToRestMapper.createEntityDetailsDTO(new ArrayList<>(emptyIfNull(entityDetailsProto)));
      if (EmptyPredicate.isNotEmpty(entityDetail)) {
        entityDetails.addAll(entityDetail);
      }
    }
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails, true);
  }

  private boolean isSecretVariable(Map.Entry<String, ?> entry) {
    Map<?, ?> value = (Map<?, ?>) entry.getValue();
    return value.containsKey(TYPE) && SECRET.equals(value.get(TYPE));
  }

  private NGVariable createSecretNGVariable(Map.Entry<String, ?> entry) {
    String secretRef = (String) ((Map<?, ?>) entry.getValue()).get(VALUE);
    SecretRefData secretRefData = SecretRefData.builder()
                                      .scope(IdentifierRefHelper.getScopeFromScopedRef(secretRef))
                                      .identifier(IdentifierRefHelper.getIdentifier(secretRef))
                                      .build();
    return SecretNGVariable.builder()
        .name(entry.getKey())
        .type(NGVariableType.SECRET)
        .value(ParameterField.createValueField(secretRefData))
        .build();
  }
}
