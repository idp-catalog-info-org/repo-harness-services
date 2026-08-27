/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.service;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.expressionEvaluator.GitOpsExpressionEvaluator;
import io.harness.cdng.expressionEvaluator.GitOpsSecretFunctor;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.common.ExpressionMode;
import io.harness.gitops.models.AgentExpressionRequest;
import io.harness.gitops.models.AgentExpressionResponse;
import io.harness.gitops.models.Expression;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.variable.services.VariableService;
import io.harness.pms.yaml.ParameterField;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * gitops expression resolution without pipeline execution context.
 * cannot reuse ServiceStepV3Helper/EnvironmentMapper utilities because:
 * - they're in 125-cd-nextgen module which depends on pipeline orchestration (Ambiance, sweeping outputs)
 * - gitops runs in 120-ng-manager with direct entity fetching, no pipeline context
 * - different execution style: pipeline uses staged resolution with outcomes, gitops uses on-demand functors
 *
 * follows the same variable merge patterns (putAll for overrides) but implements independently for gitops context.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
@OwnedBy(GITOPS)
@Slf4j
public class GitOpsExpressionServiceImpl implements GitOpsExpressionService {
  @Inject @Named("PRIVILEGED") private SecretManagerClientService ngSecretService;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private VariableService variableService;
  @Inject private ScopeInfoService scopeInfoService;
  @Inject private ServiceEntityService serviceEntityService;
  @Inject private EnvironmentService environmentService;
  @Inject private ServiceOverridesServiceV2 serviceOverridesServiceV2;

  @Override
  public AgentExpressionResponse getExpression(AgentExpressionRequest agentExpressionRequest) {
    validateRequest(agentExpressionRequest);

    Map<String, Object> serviceVariables = fetchServiceVariables(agentExpressionRequest);
    Map<String, Object> environmentVariables = fetchEnvironmentVariables(agentExpressionRequest);
    Environment environment = fetchEnvironment(agentExpressionRequest);

    GitOpsExpressionEvaluator evaluator =
        createExpressionEvaluator(agentExpressionRequest, serviceVariables, environmentVariables, environment);

    List<Expression> evaluatedExpressions = evaluateExpressions(agentExpressionRequest, evaluator);
    return buildAgentExpressionResponse(evaluatedExpressions, evaluator);
  }

  private GitOpsExpressionEvaluator createExpressionEvaluator(AgentExpressionRequest agentExpressionRequest,
      Map<String, Object> serviceVariables, Map<String, Object> environmentVariables, Environment environment) {
    return new GitOpsExpressionEvaluator(agentExpressionRequest.getAccountIdentifier(),
        agentExpressionRequest.getOrgIdentifier(), agentExpressionRequest.getProjectIdentifier(),
        agentExpressionRequest.getToken(), ngSecretService, variableService, scopeInfoService, serviceVariables,
        environmentVariables, environment);
  }

  List<Expression> evaluateExpressions(
      AgentExpressionRequest agentExpressionRequest, GitOpsExpressionEvaluator expressionEvaluator) {
    List<Expression> evaluatedExpressions = new ArrayList<>();
    Map<String, Object> contextMap = prepareContextMap(agentExpressionRequest.getContext());

    for (Expression expression : agentExpressionRequest.getExpressions()) {
      Expression evaluatedExpression = evaluateSingleExpression(expression, contextMap, expressionEvaluator);
      evaluatedExpressions.add(evaluatedExpression);
    }

    return evaluatedExpressions;
  }

  private Map<String, Object> prepareContextMap(Map<String, String> context) {
    return context != null ? new HashMap<>(context) : new HashMap<>();
  }

  private Expression evaluateSingleExpression(
      Expression expression, Map<String, Object> contextMap, GitOpsExpressionEvaluator expressionEvaluator) {
    String evaluatedValue = expressionEvaluator.renderExpressionWithSecretTracking(expression.getIndex(),
        expression.getValue(), contextMap, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);

    boolean isSecret = expressionEvaluator.wasExpressionSecretResolution(expression.getIndex());

    return Expression.builder().value(evaluatedValue).index(expression.getIndex()).isSecret(isSecret).build();
  }

  AgentExpressionResponse buildAgentExpressionResponse(
      List<Expression> evaluatedExpressions, GitOpsExpressionEvaluator expressionEvaluator) {
    GitOpsSecretFunctor secretFunctor = expressionEvaluator.getGitOpsSecretFunctor();
    Preconditions.checkState(secretFunctor != null, "GitOpsSecretFunctor cannot be null");

    return AgentExpressionResponse.builder()
        .expressions(evaluatedExpressions)
        .encryptionConfigs(secretFunctor.getEncryptionConfigs())
        .secrets(secretFunctor.getSecretDetails())
        .build();
  }

  @VisibleForTesting
  void validateRequest(AgentExpressionRequest agentExpressionRequest) {
    if (agentExpressionRequest == null) {
      throw new InvalidArgumentsException("AgentExpressionRequest is empty/null");
    }
    if (agentExpressionRequest.getExpressions() == null || agentExpressionRequest.getExpressions().isEmpty()) {
      throw new InvalidArgumentsException("AgentExpressionRequest has null or empty expressions field");
    }
    if (EmptyPredicate.isEmpty(agentExpressionRequest.getAccountIdentifier())) {
      throw new InvalidArgumentsException("AccountIdentifier cannot be empty");
    }

    if (EmptyPredicate.isNotEmpty(agentExpressionRequest.getApplication())) {
      // application context present — require full scope (org + project)
      if (EmptyPredicate.isEmpty(agentExpressionRequest.getOrgIdentifier())) {
        throw new InvalidArgumentsException("OrgIdentifier cannot be empty");
      }
      if (EmptyPredicate.isEmpty(agentExpressionRequest.getProjectIdentifier())) {
        throw new InvalidArgumentsException("ProjectIdentifier cannot be empty");
      }
    } else {
      // no application context — validate scope based on expression references
      validateScopeFromExpressions(agentExpressionRequest);
    }

    if (!cdFeatureFlagHelper.isEnabled(
            agentExpressionRequest.getAccountIdentifier(), FeatureName.CDS_GITOPS_SECRET_RESOLUTION_ENABLED)) {
      throw new InvalidRequestException(
          "GitOpsSecretResolution feature (Flag: CDS_GITOPS_SECRET_RESOLUTION_ENABLED) is disabled.");
    }
  }

  private void validateScopeFromExpressions(AgentExpressionRequest agentExpressionRequest) {
    for (Expression expression : agentExpressionRequest.getExpressions()) {
      String value = expression.getValue();
      if (value == null) {
        continue;
      }
      if (requiresProjectScope(value)) {
        if (EmptyPredicate.isEmpty(agentExpressionRequest.getOrgIdentifier())) {
          throw new InvalidArgumentsException("OrgIdentifier cannot be empty for project-scoped expression: " + value);
        }
        if (EmptyPredicate.isEmpty(agentExpressionRequest.getProjectIdentifier())) {
          throw new InvalidArgumentsException(
              "ProjectIdentifier cannot be empty for project-scoped expression: " + value);
        }
      } else if (requiresOrgScope(value)) {
        if (EmptyPredicate.isEmpty(agentExpressionRequest.getOrgIdentifier())) {
          throw new InvalidArgumentsException("OrgIdentifier cannot be empty for org-scoped expression: " + value);
        }
      }
    }
  }

  private boolean requiresProjectScope(String expression) {
    // project-scoped: secrets.getValue("secretId") without account. or org. prefix
    if (!expression.contains("secrets.getValue")) {
      return false;
    }
    return !expression.contains("\"account.") && !expression.contains("\"org.");
  }

  private boolean requiresOrgScope(String expression) {
    return expression.contains("\"org.");
  }

  /**
   * Fetches service variables with ENV_SERVICE overrides applied.
   *
   * Override Priority for service variables:
   * 1. Base service variables (from service YAML)
   * 2. ENV_SERVICE overrides (service+environment specific) - HIGHEST PRIORITY
   *
   * Example: service "svc1" has imageTag=v1.0
   *          ENV_SERVICE override for svc1+env1 sets imageTag=v2.0
   *          Result: svc1 in env1 → imageTag=v2.0, svc1 in env2 → imageTag=v1.0
   */
  private Map<String, Object> fetchServiceVariables(AgentExpressionRequest agentExpressionRequest) {
    if (EmptyPredicate.isEmpty(agentExpressionRequest.getServiceRef())) {
      return new HashMap<>();
    }

    try {
      String serviceRef = agentExpressionRequest.getServiceRef();
      String serviceIdentifier = getServiceIdentifierFromRef(serviceRef);
      ScopeInfo scopeInfo = getScopeInfoFromServiceRef(agentExpressionRequest, serviceRef);

      Optional<ServiceEntity> serviceEntityOpt = serviceEntityService.get(scopeInfo, serviceIdentifier, false);
      if (!serviceEntityOpt.isPresent() || EmptyPredicate.isEmpty(serviceEntityOpt.get().getYaml())) {
        return new HashMap<>();
      }

      NGServiceConfig serviceConfig = YamlPipelineUtils.read(serviceEntityOpt.get().getYaml(), NGServiceConfig.class);
      List<NGVariable> serviceVariables = getVariablesFromConfig(serviceConfig);
      Map<String, Object> baseVariables = convertToVariablesMap(serviceVariables);

      // merge ENV_SERVICE overrides (service+environment specific) on top of base service variables
      Map<String, Object> envServiceOverrides = fetchEnvServiceOverrides(agentExpressionRequest);
      return mergeVariables(baseVariables, envServiceOverrides);
    } catch (Exception e) {
      log.error("Failed to fetch service variables for serviceRef: {}", agentExpressionRequest.getServiceRef(), e);
      return new HashMap<>();
    }
  }

  private List<NGVariable> getVariablesFromConfig(NGServiceConfig serviceConfig) {
    if (serviceConfig == null || serviceConfig.getNgServiceV2InfoConfig() == null
        || serviceConfig.getNgServiceV2InfoConfig().getServiceDefinition() == null
        || serviceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec() == null) {
      return Collections.emptyList();
    }

    List<NGVariable> variables =
        serviceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec().getVariables();

    return variables != null ? variables : Collections.emptyList();
  }

  private Map<String, Object> convertToVariablesMap(List<NGVariable> variables) {
    Map<String, Object> variablesMap = new HashMap<>();
    if (variables == null) {
      return variablesMap;
    }

    for (NGVariable variable : variables) {
      Object value;

      // Handle secret variables - convert to secret expression
      if (variable instanceof io.harness.yaml.core.variables.SecretNGVariable) {
        io.harness.yaml.core.variables.SecretNGVariable secretVar =
            (io.harness.yaml.core.variables.SecretNGVariable) variable;
        ParameterField<SecretRefData> secretValue = (ParameterField<SecretRefData>) secretVar.getCurrentValue();

        if (secretValue != null && !ParameterField.isNull(secretValue)) {
          if (secretValue.isExpression()) {
            value = secretValue.getExpressionValue();
          } else if (secretValue.getValue() != null && !secretValue.getValue().isNull()) {
            // convert SecretRefData to secret expression format
            String secretRef = secretValue.getValue().toSecretRefStringValue();
            value = NGVariablesUtils.fetchSecretExpression(secretRef);
          } else {
            value = null;
          }
        } else {
          value = null;
        }
      } else {
        // Handle non-secret variables - extract actual value
        ParameterField<?> currentValue = variable.getCurrentValue();
        if (currentValue != null && currentValue.isExpression()) {
          value = currentValue.getExpressionValue();
        } else {
          value = ParameterField.isNull(currentValue) ? null : currentValue.getValue();
        }
      }

      if (value != null) {
        variablesMap.put(variable.getName(), value);
      }
    }

    return variablesMap;
  }

  private ScopeInfo getScopeInfoFromServiceRef(AgentExpressionRequest request, String serviceRef) {
    if (serviceRef.startsWith("account.")) {
      return scopeInfoService.getScopeInfo(request.getAccountIdentifier(), null, null);
    } else if (serviceRef.startsWith("org.")) {
      return scopeInfoService.getScopeInfo(request.getAccountIdentifier(), request.getOrgIdentifier(), null);
    }
    return scopeInfoService.getScopeInfo(
        request.getAccountIdentifier(), request.getOrgIdentifier(), request.getProjectIdentifier());
  }

  private String getServiceIdentifierFromRef(String serviceRef) {
    if (serviceRef.startsWith("account.")) {
      return serviceRef.substring("account.".length());
    } else if (serviceRef.startsWith("org.")) {
      return serviceRef.substring("org.".length());
    }
    return serviceRef;
  }

  private Environment fetchEnvironment(AgentExpressionRequest agentExpressionRequest) {
    if (EmptyPredicate.isEmpty(agentExpressionRequest.getEnvRef())) {
      return null;
    }

    try {
      String envRef = agentExpressionRequest.getEnvRef();
      ScopeInfo scopeInfo = getScopeInfoFromEnvRef(agentExpressionRequest, envRef);
      String envIdentifier = getEnvironmentIdentifierFromRef(envRef);

      Optional<Environment> environmentOpt = environmentService.get(scopeInfo, envIdentifier, false);
      return environmentOpt.orElse(null);
    } catch (Exception e) {
      log.error("Failed to fetch environment for envRef: {}", agentExpressionRequest.getEnvRef(), e);
      return null;
    }
  }

  /**
   * Fetches environment variables with ENV_GLOBAL overrides applied.
   *
   * Override Priority for environment variables:
   * 1. Base environment variables (from environment YAML)
   * 2. ENV_GLOBAL overrides (environment-level global) - HIGHEST PRIORITY
   *
   * Note: ENV_SERVICE overrides affect SERVICE variables, not environment variables.
   */
  private Map<String, Object> fetchEnvironmentVariables(AgentExpressionRequest agentExpressionRequest) {
    if (EmptyPredicate.isEmpty(agentExpressionRequest.getEnvRef())) {
      return new HashMap<>();
    }

    try {
      Environment environment = fetchEnvironment(agentExpressionRequest);
      if (environment == null || EmptyPredicate.isEmpty(environment.getYaml())) {
        return new HashMap<>();
      }

      NGEnvironmentConfig envConfig = YamlPipelineUtils.read(environment.getYaml(), NGEnvironmentConfig.class);
      List<NGVariable> envVariables = getVariablesFromEnvConfig(envConfig);
      Map<String, Object> baseVariables = convertToVariablesMap(envVariables);

      Map<String, Object> envGlobalOverrides = fetchEnvGlobalOverrides(agentExpressionRequest);
      return mergeVariables(baseVariables, envGlobalOverrides);
    } catch (Exception e) {
      log.error("Failed to fetch environment variables for envRef: {}", agentExpressionRequest.getEnvRef(), e);
      return new HashMap<>();
    }
  }

  private List<NGVariable> getVariablesFromEnvConfig(NGEnvironmentConfig envConfig) {
    if (envConfig == null || envConfig.getNgEnvironmentInfoConfig() == null) {
      return Collections.emptyList();
    }

    List<NGVariable> variables = envConfig.getNgEnvironmentInfoConfig().getVariables();
    return variables != null ? variables : Collections.emptyList();
  }

  /**
   * Fetches ENV_SERVICE overrides (service+environment specific).
   * These override SERVICE variables, not environment variables.
   * Lookup by environmentRef + serviceRef.
   */
  private Map<String, Object> fetchEnvServiceOverrides(AgentExpressionRequest agentExpressionRequest) {
    if (EmptyPredicate.isEmpty(agentExpressionRequest.getServiceRef())
        || EmptyPredicate.isEmpty(agentExpressionRequest.getEnvRef())) {
      return new HashMap<>();
    }

    try {
      ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(agentExpressionRequest.getAccountIdentifier(),
          agentExpressionRequest.getOrgIdentifier(), agentExpressionRequest.getProjectIdentifier());

      // ENV_SERVICE override: lookup by environmentRef + serviceRef
      Optional<NGServiceOverridesEntity> overrideEntityOpt = serviceOverridesServiceV2.get(
          scopeInfo, agentExpressionRequest.getEnvRef(), agentExpressionRequest.getServiceRef(), null, false, false);

      if (!overrideEntityOpt.isPresent()) {
        return new HashMap<>();
      }

      NGServiceOverridesEntity envServiceEntity = overrideEntityOpt.get();
      // Use spec directly - it's already populated by ServiceOverridesServiceV2
      if (envServiceEntity.getSpec() == null) {
        return new HashMap<>();
      }

      List<NGVariable> overrideVars = envServiceEntity.getSpec().getVariables();
      return convertToVariablesMap(overrideVars != null ? overrideVars : Collections.emptyList());
    } catch (Exception e) {
      log.error("Failed to fetch ENV_SERVICE overrides for serviceRef: {}, envRef: {}",
          agentExpressionRequest.getServiceRef(), agentExpressionRequest.getEnvRef(), e);
      return new HashMap<>();
    }
  }

  /**
   * Fetches ENV_GLOBAL overrides (environment-level global).
   * These provide additional environment variables, not service variable overrides.
   * Lookup by environmentRef only (serviceRef=null).
   */
  private Map<String, Object> fetchEnvGlobalOverrides(AgentExpressionRequest agentExpressionRequest) {
    if (EmptyPredicate.isEmpty(agentExpressionRequest.getEnvRef())) {
      return new HashMap<>();
    }

    try {
      ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(agentExpressionRequest.getAccountIdentifier(),
          agentExpressionRequest.getOrgIdentifier(), agentExpressionRequest.getProjectIdentifier());

      Optional<NGServiceOverridesEntity> overrideEntityOpt =
          serviceOverridesServiceV2.get(scopeInfo, agentExpressionRequest.getEnvRef(), null, null, false, false);

      if (!overrideEntityOpt.isPresent()) {
        return new HashMap<>();
      }

      NGServiceOverridesEntity entity = overrideEntityOpt.get();
      if (entity.getSpec() == null) {
        return new HashMap<>();
      }

      List<NGVariable> overrideVars = entity.getSpec().getVariables();
      return convertToVariablesMap(overrideVars != null ? overrideVars : Collections.emptyList());
    } catch (Exception e) {
      log.error("Failed to fetch ENV_GLOBAL overrides for envRef: {}", agentExpressionRequest.getEnvRef(), e);
      return new HashMap<>();
    }
  }

  /**
   * merges override variables on top of base variables using putAll pattern.
   *
   * matches established pattern in ServiceStepV3Helper.getAllOverridesVariables() and
   * EnvironmentMapper.overrideVariables()
   * - both use map.putAll() for override precedence
   * - both follow OVERRIDE_IN_REVERSE_PRIORITY constant for layered overrides
   *
   * gitops priority (simpler than pipeline's 4-level hierarchy):
   * - service variables: base service < ENV_SERVICE overrides
   * - environment variables: base env < ENV_GLOBAL overrides
   */
  private Map<String, Object> mergeVariables(Map<String, Object> baseVariables, Map<String, Object> overrideVariables) {
    Map<String, Object> merged = new HashMap<>(baseVariables);
    merged.putAll(overrideVariables);
    return merged;
  }

  private ScopeInfo getScopeInfoFromEnvRef(AgentExpressionRequest request, String envRef) {
    if (envRef.startsWith("account.")) {
      return scopeInfoService.getScopeInfo(request.getAccountIdentifier(), null, null);
    } else if (envRef.startsWith("org.")) {
      return scopeInfoService.getScopeInfo(request.getAccountIdentifier(), request.getOrgIdentifier(), null);
    }
    return scopeInfoService.getScopeInfo(
        request.getAccountIdentifier(), request.getOrgIdentifier(), request.getProjectIdentifier());
  }

  private String getEnvironmentIdentifierFromRef(String envRef) {
    if (envRef.startsWith("account.")) {
      return envRef.substring("account.".length());
    } else if (envRef.startsWith("org.")) {
      return envRef.substring("org.".length());
    }
    return envRef;
  }
}