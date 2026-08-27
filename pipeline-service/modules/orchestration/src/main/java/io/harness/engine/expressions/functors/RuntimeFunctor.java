/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.expression.ConnectorInputsMapper;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.LateBindingMap;
import io.harness.expression.celcustomfunctor.RuntimeCelFunctor;
import io.harness.expression.celcustomfunctor.WithToString;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.runner.request.helpers.infra.InfraBasedHelper;
import io.harness.utils.CDStepsExpressionResolver;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.collect.ImmutableMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Slf4j
public class RuntimeFunctor extends LateBindingMap implements WithToString, RuntimeCelFunctor {
  public static final String FQN = "fqn";
  public static final String WORKSPACE = "workspace";
  public static final String MANIFEST_PATH = "manifestPath";
  public static final String MANIFEST_FLAGS = "manifestFlags";
  public static final String MANIFEST_OVERRIDES = "manifestOverrides";
  public static final String CACHE_PORT = "cachePort";
  public static final String DLC_CACHE_ARGS = "dlcCacheArgs";
  private static final String DEFAULT_CACHE_SERVER_PORT = "8082";
  private static final String DLC_CACHE_KEY_BACKEND = "backend";
  private static final String DLC_CACHE_KEY_BUCKET = "bucket";
  private static final String DLC_CACHE_KEY_ENDPOINT_URL = "endpoint_url";
  private static final String DLC_CACHE_KEY_REGION = "region";
  private static final String DLC_CACHE_KEY_ACCOUNT_NAME = "account_name";
  private static final String DLC_CACHE_KEY_CONTAINER_NAME = "container_name";
  private static final String DLC_CACHE_KEY_CONNECTOR_REF = "connectorRef";
  private static final String DLC_CACHE_EXPR_BACKEND = "${{runtime.inputs.PLUGIN_BACKEND}}";
  private static final String DLC_CACHE_EXPR_BUCKET = "${{runtime.settings.ci_cache_s3_bucket_name}}";
  private static final String DLC_CACHE_EXPR_ENDPOINT_URL = "${{runtime.settings.ci_cache_s3_endpoint_url}}";
  private static final String DLC_CACHE_EXPR_REGION = "${{runtime.settings.ci_cache_s3_region}}";
  private static final String DLC_CACHE_EXPR_ACCOUNT_NAME = "${{runtime.settings.ci_cache_azure_account_name}}";
  private static final String DLC_CACHE_EXPR_CONTAINER_NAME = "${{runtime.settings.ci_cache_azure_container_name}}";
  private static final String DLC_CACHE_EXPR_CONNECTOR_REF = "${{runtime.settings.ci_cache_connector}}";
  private static final String DLC_CACHE_ARG_TYPE = "type";
  private static final String DLC_CACHE_ARG_ACCESS_KEY_ID = "access_key_id";
  private static final String DLC_CACHE_ARG_SECRET_ACCESS_KEY = "secret_access_key";
  private static final String DLC_CACHE_ARG_ASSUME_ROLE_ARN = "assume_role_arn";
  private static final String DLC_CACHE_ARG_OIDC_TOKEN_ID = "oidc_token_id";
  private static final String DLC_CACHE_ARG_GCP_JSON_KEY = "gcp_json_key";
  private static final String DLC_CACHE_ARG_OIDC_PROJECT_ID = "oidc_project_id";
  private static final String DLC_CACHE_ARG_OIDC_POOL_ID = "oidc_pool_id";
  private static final String DLC_CACHE_ARG_OIDC_PROVIDER_ID = "oidc_provider_id";
  private static final String DLC_CACHE_ARG_OIDC_SERVICE_ACCOUNT_EMAIL = "oidc_service_account_email";
  private static final String DLC_CACHE_ARG_CLIENT_ID = "client_id";
  private static final String DLC_CACHE_ARG_TENANT_ID = "tenant_id";
  private static final String DLC_CACHE_ARG_CLIENT_SECRET = "client_secret";
  private static final String CONNECTOR_FIELD_ACCESS_KEY = "accessKey";
  private static final String CONNECTOR_FIELD_SECRET_KEY = "secretKey";
  private static final String CONNECTOR_FIELD_IAM_ROLE_ARN = "iamRoleArn";
  private static final String CONNECTOR_FIELD_OIDC_TOKEN = "oidcToken";
  private static final String CONNECTOR_FIELD_JSON_KEY = "jsonKey";
  private static final String CONNECTOR_FIELD_GCP_PROJECT_ID = "gcpProjectId";
  private static final String CONNECTOR_FIELD_WORKLOAD_POOL_ID = "workloadPoolId";
  private static final String CONNECTOR_FIELD_PROVIDER_ID = "providerId";
  private static final String CONNECTOR_FIELD_SERVICE_ACCOUNT_EMAIL = "serviceAccountEmail";
  private static final String CONNECTOR_FIELD_CLIENT_ID = "clientId";
  private static final String CONNECTOR_FIELD_TENANT_ID = "tenantId";
  private static final String CONNECTOR_FIELD_CLIENT_SECRET = "clientSecret";
  private static final String GCP_JSON_KEY_TEMPLATE = "<{ %s | getAsBase64 }>";

  Ambiance ambiance;
  List<RuntimeAbstractFunctor> runtimeAbstractFunctors;
  InfraBasedHelper infraBasedHelper;
  CDStepsExpressionResolver cdStepsExpressionResolver;
  ConnectorInputsMapper connectorInputsMapper;

  @Builder
  public RuntimeFunctor(Ambiance ambiance, List<RuntimeAbstractFunctor> runtimeAbstractFunctors,
      InfraBasedHelper infraBasedHelper, CDStepsExpressionResolver cdStepsExpressionResolver,
      ConnectorInputsMapper connectorInputsMapper) {
    this.ambiance = ambiance;
    this.runtimeAbstractFunctors = runtimeAbstractFunctors;
    this.infraBasedHelper = infraBasedHelper;
    this.cdStepsExpressionResolver = cdStepsExpressionResolver;
    this.connectorInputsMapper = connectorInputsMapper;
  }

  private static final Map<String, Function<RuntimeFunctor, Object>> KEY_HANDLERS =
      ImmutableMap.<String, Function<RuntimeFunctor, Object>>builder()
          .put(FQN, RuntimeFunctor::handleFQN)
          .put(WORKSPACE, RuntimeFunctor::handleWorkspace)
          .put(MANIFEST_PATH, RuntimeFunctor::handleManifestPath)
          .put(CACHE_PORT, RuntimeFunctor::handleCachePort)
          .put(DLC_CACHE_ARGS, RuntimeFunctor::handleDlcCacheArgs)
          .put(MANIFEST_FLAGS, RuntimeFunctor::handleManifestFlags)
          .put(MANIFEST_OVERRIDES, RuntimeFunctor::handleManifestOverrides)
          .build();

  @Override
  public Object getParentFqn(Integer level) {
    return this.handleParentFQN(level);
  }

  @Override
  public synchronized Object get(Object key) {
    if (!(key instanceof String functorKey)) {
      return null;
    }

    Function<RuntimeFunctor, Object> handler = KEY_HANDLERS.get(functorKey);
    if (handler != null) {
      return handler.apply(this);
    }

    // Check runtime functors if no static handler exists
    return runtimeAbstractFunctors.stream()
        .filter(functor -> functor.supportsKey(functorKey))
        .findFirst()
        .map(functor -> {
          if (functor instanceof io.harness.expression.LateBindingValue lateBindingValue) {
            return lateBindingValue.bind();
          }
          return functor;
        })
        .orElse(null);
  }

  // This is required for CEL because CEL first calls the containsKey method and only if is true does it call get method
  // where we have our logic. That's why we are returning true here so that it can go to the get method.
  @Override
  public boolean containsKey(Object key) {
    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      return true;
    } else {
      return super.containsKey(key);
    }
  }

  private String handleWorkspace() {
    Infrastructure infrastructure = infraBasedHelper.getStageInfra(ambiance);
    return infraBasedHelper.getBasePath(ambiance, infrastructure);
  }

  String handleFQN() {
    String fqnUsingLevels = AmbianceUtils.getFQNUsingLevels(ambiance.getLevelsList());
    return fqnUsingLevels.contains(".") ? fqnUsingLevels.substring(0, fqnUsingLevels.lastIndexOf('.')) : fqnUsingLevels;
  }

  String handleParentFQN(int level) {
    String fqn = handleFQN();
    if (level <= 0) {
      return fqn;
    }

    String currentFQN = fqn;
    for (int currentLevel = level; currentLevel > 0; currentLevel--) {
      String firstLevel = currentFQN.contains(".") ? currentFQN.substring(0, currentFQN.lastIndexOf('.')) : currentFQN;
      currentFQN = firstLevel.contains(".") ? firstLevel.substring(0, firstLevel.lastIndexOf('.')) : firstLevel;
    }

    return currentFQN;
  }

  private String handleManifestFlags() {
    String flagsJson = (String) cdStepsExpressionResolver.updateExpressions(
        ambiance, "${{serviceOutput.manifests.primary.inputs.flags}}", ExpressionMode.RETURN_NULL_IF_UNRESOLVED);

    return flagsJson != null ? flagsJson.replaceAll("\"commandType\"", "\"command\"") : null;
  }

  private String handleManifestOverrides() {
    return (String) cdStepsExpressionResolver.updateExpressions(
        ambiance, "${{serviceOutput.manifests.overrides}}", ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
  }

  private String handleManifestPath() {
    String templatingPath = (String) cdStepsExpressionResolver.updateExpressions(ambiance,
        "${{stage.steps.harnessTemplating.output.PLUGIN_RENDERED_MANIFEST_OUTPUT_PATH}}",
        ExpressionMode.RETURN_NULL_IF_UNRESOLVED);

    if (isPresent(templatingPath)) {
      return templatingPath;
    }

    String artifactDownloadPath = (String) cdStepsExpressionResolver.updateExpressions(
        ambiance, "${{manifests.primary.ARTIFACT_DOWNLOAD_PATH}}", ExpressionMode.RETURN_NULL_IF_UNRESOLVED);

    if (isPresent(artifactDownloadPath)) {
      return artifactDownloadPath;
    }

    // TODO: @Tathagat remove this fallback
    artifactDownloadPath = (String) cdStepsExpressionResolver.updateExpressions(
        ambiance, "${{manifests.primary.PLUGIN_ARTIFACT_DOWNLOAD_PATH}}", ExpressionMode.RETURN_NULL_IF_UNRESOLVED);

    if (isPresent(artifactDownloadPath)) {
      return artifactDownloadPath;
    }

    return (String) cdStepsExpressionResolver.updateExpressions(
        ambiance, "${{serviceOutput.manifests.primary.paths}}", ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
  }

  private boolean isPresent(String value) {
    return value != null && !value.isEmpty() && !"null".equals(value);
  }

  private String handleCachePort() {
    String stageCacheValue = (String) cdStepsExpressionResolver.updateExpressions(
        ambiance, "${{stage.buildIntelligence.port}}", ExpressionMode.RETURN_NULL_IF_UNRESOLVED);

    if (isPresent(stageCacheValue)) {
      return stageCacheValue;
    }

    String settingsValue = (String) cdStepsExpressionResolver.updateExpressions(
        ambiance, "${{runtime.settings.ci_build_intel_cache_server_port}}", ExpressionMode.RETURN_NULL_IF_UNRESOLVED);

    if (isPresent(settingsValue)) {
      return settingsValue;
    }

    return DEFAULT_CACHE_SERVER_PORT;
  }

  @SuppressWarnings("unchecked")
  String handleDlcCacheArgs() {
    StringBuilder sb = new StringBuilder();

    Map<String, String> expressionMap = new LinkedHashMap<>();
    expressionMap.put(DLC_CACHE_KEY_BACKEND, DLC_CACHE_EXPR_BACKEND);
    expressionMap.put(DLC_CACHE_KEY_BUCKET, DLC_CACHE_EXPR_BUCKET);
    expressionMap.put(DLC_CACHE_KEY_ENDPOINT_URL, DLC_CACHE_EXPR_ENDPOINT_URL);
    expressionMap.put(DLC_CACHE_KEY_REGION, DLC_CACHE_EXPR_REGION);
    expressionMap.put(DLC_CACHE_KEY_ACCOUNT_NAME, DLC_CACHE_EXPR_ACCOUNT_NAME);
    expressionMap.put(DLC_CACHE_KEY_CONTAINER_NAME, DLC_CACHE_EXPR_CONTAINER_NAME);
    expressionMap.put(DLC_CACHE_KEY_CONNECTOR_REF, DLC_CACHE_EXPR_CONNECTOR_REF);

    Map<String, String> resolved = (Map<String, String>) cdStepsExpressionResolver.updateExpressions(
        ambiance, expressionMap, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);

    String backend = resolved.get(DLC_CACHE_KEY_BACKEND);
    appendIfPresent(sb, DLC_CACHE_ARG_TYPE, backend);
    appendIfPresent(sb, DLC_CACHE_KEY_BUCKET, resolved.get(DLC_CACHE_KEY_BUCKET));
    appendIfPresent(sb, DLC_CACHE_KEY_ACCOUNT_NAME, resolved.get(DLC_CACHE_KEY_ACCOUNT_NAME));
    appendIfPresent(sb, DLC_CACHE_KEY_CONTAINER_NAME, resolved.get(DLC_CACHE_KEY_CONTAINER_NAME));
    appendIfPresent(sb, DLC_CACHE_KEY_ENDPOINT_URL, resolved.get(DLC_CACHE_KEY_ENDPOINT_URL));
    appendIfPresent(sb, DLC_CACHE_KEY_REGION, resolved.get(DLC_CACHE_KEY_REGION));

    String connectorRef = resolved.get(DLC_CACHE_KEY_CONNECTOR_REF);
    if (isPresent(connectorRef)) {
      Map<String, Object> connectorFields = fetchConnectorFields(connectorRef);
      if (connectorFields != null && !connectorFields.isEmpty()) {
        appendIfResolved(sb, DLC_CACHE_ARG_ACCESS_KEY_ID, getStringField(connectorFields, CONNECTOR_FIELD_ACCESS_KEY));
        appendIfResolved(
            sb, DLC_CACHE_ARG_SECRET_ACCESS_KEY, getStringField(connectorFields, CONNECTOR_FIELD_SECRET_KEY));
        appendIfResolved(
            sb, DLC_CACHE_ARG_ASSUME_ROLE_ARN, getStringField(connectorFields, CONNECTOR_FIELD_IAM_ROLE_ARN));
        appendIfResolved(sb, DLC_CACHE_ARG_OIDC_TOKEN_ID, getStringField(connectorFields, CONNECTOR_FIELD_OIDC_TOKEN));
        String jsonKey = getStringField(connectorFields, CONNECTOR_FIELD_JSON_KEY);
        if (isPresent(jsonKey)) {
          String t = String.format(GCP_JSON_KEY_TEMPLATE, jsonKey);
          appendIfPresent(sb, DLC_CACHE_ARG_GCP_JSON_KEY, t);
        }
        appendIfResolved(
            sb, DLC_CACHE_ARG_OIDC_PROJECT_ID, getStringField(connectorFields, CONNECTOR_FIELD_GCP_PROJECT_ID));
        appendIfResolved(
            sb, DLC_CACHE_ARG_OIDC_POOL_ID, getStringField(connectorFields, CONNECTOR_FIELD_WORKLOAD_POOL_ID));
        appendIfResolved(
            sb, DLC_CACHE_ARG_OIDC_PROVIDER_ID, getStringField(connectorFields, CONNECTOR_FIELD_PROVIDER_ID));
        appendIfResolved(sb, DLC_CACHE_ARG_OIDC_SERVICE_ACCOUNT_EMAIL,
            getStringField(connectorFields, CONNECTOR_FIELD_SERVICE_ACCOUNT_EMAIL));
        appendIfResolved(sb, DLC_CACHE_ARG_CLIENT_ID, getStringField(connectorFields, CONNECTOR_FIELD_CLIENT_ID));
        appendIfResolved(sb, DLC_CACHE_ARG_TENANT_ID, getStringField(connectorFields, CONNECTOR_FIELD_TENANT_ID));
        appendIfResolved(
            sb, DLC_CACHE_ARG_CLIENT_SECRET, getStringField(connectorFields, CONNECTOR_FIELD_CLIENT_SECRET));
      }
    }
    return sb.toString();
  }

  private Map<String, Object> fetchConnectorFields(String connectorRef) {
    try {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgId, projectId);
      return connectorInputsMapper.fetchConnectorFieldsDetails(identifierRef.getIdentifier(),
          identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(),
          ambiance);
    } catch (Exception ex) {
      log.error("Failed to fetch connector fields for connectorRef: {}", connectorRef, ex);
      return null;
    }
  }

  private String getStringField(Map<String, Object> fields, String key) {
    Object value = fields.get(key);
    return value instanceof String ? (String) value : null;
  }

  private void appendIfPresent(StringBuilder sb, String key, String value) {
    if (isPresent(value)) {
      if (sb.length() > 0) {
        sb.append(',');
      }
      sb.append(key).append('=').append(value);
    }
  }

  private void appendIfResolved(StringBuilder sb, String key, String value) {
    if (isPresent(value) && !EngineExpressionEvaluator.hasExpressions(value)) {
      if (sb.length() > 0) {
        sb.append(',');
      }
      sb.append(key).append('=').append(value);
    }
  }

  @Override
  public String toString(Object o) {
    if (o instanceof List) {
      List<?> list = (List<?>) o;
      boolean allStrings = list.stream().allMatch(item -> item instanceof String);
      if (allStrings) {
        List<String> stringList = (List<String>) list;
        return FunctorHelper.sliceListToString(stringList);
      }
    } else if (o instanceof String[]) {
      String[] stringList = (String[]) o;
      return FunctorHelper.sliceStringArrayToString(stringList);
    } else if (o instanceof Map<?, ?>) {
      Map<?, ?> map = (Map<?, ?>) o;
      boolean allStrings = map.keySet().stream().allMatch(k -> k instanceof String)
          && map.values().stream().allMatch(v -> v instanceof String);
      if (allStrings) {
        Map<String, String> stringMap = (Map<String, String>) map;
        return FunctorHelper.sliceMapToString(stringMap);
      }
    }
    log.warn("Object provided should be map or list with data type string");
    return null;
  }
}
