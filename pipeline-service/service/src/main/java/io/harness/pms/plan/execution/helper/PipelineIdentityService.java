/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.harnessid.client.HarnessIdClientService;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.IdentityDeclaredLevel;
import io.harness.pms.contracts.ambiance.IdentityEntry;
import io.harness.pms.contracts.ambiance.IdentityExecutionContext;
import io.harness.pms.execution.utils.IdentityContextMerger.DisabledState;
import io.harness.pms.utils.PmsGrpcClientUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.workloadidentity.DeclaredIdentityExtractor;
import io.harness.steps.workloadidentity.IdentitySpec;
import io.harness.steps.workloadidentity.IdentityValidator;
import io.harness.utils.PmsFeatureFlagHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.harness.harnessid.proto.workload.v1.PipelineContext;
import com.harness.harnessid.proto.workload.v1.WorkloadContext;
import com.harness.harnessid.proto.workload.v1.WorkloadRegistrationRequest;
import com.harness.harnessid.proto.workload.v1.WorkloadRegistrationResponse;
import com.harness.harnessid.proto.workload.v1.WorkloadType;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Registers one workload token per pipeline-level identity (behind PIPE_PIPELINE_IDENTITY + HarnessID config)
 * into an {@link IdentityExecutionContext} seeded on the root ambiance. A single identity's registration
 * failure never fails plan execution (§4.8): its entry is kept with an empty token, logged and counted.
 * Only literal (non-expression) audiences/claims are stored - expressions are resolved later at the step leg.
 *
 * <p>Design Decision: Workload tokens are tied to a TTL and there is no explicit revoke/unregister RPC called
 * upon plan abort/failure. Unused tokens will naturally expire on the HarnessID side (TTL-only cleanup).
 */
@Singleton
@OwnedBy(PIPELINE)
@Slf4j
public class PipelineIdentityService {
  public static final String IDENTITY_REGISTER_SUCCESS_METRIC = "pipeline_identity_register_success";
  public static final String IDENTITY_REGISTER_FAILURE_METRIC = "pipeline_identity_register_failure";
  public static final String IDENTITY_NULL_TOKEN_METRIC = "pipeline_identity_null_token_count";
  // Desired TTL is 30 days (2_592_000s) — pipeline executions can run for days and the workload token
  // must outlive them. Currently capped at 86400 (24h) by HarnessID's @Max(86400) server-side validation.
  // TODO(PIPE-follow-up): bump to 2_592_000 once HarnessID raises their @Max constraint and redeploys.
  private static final int PIPELINE_WORKLOAD_TOKEN_TTL_SECONDS = 86400;

  @Inject private HarnessIdClientService harnessIdClientService;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private MetricService metricService;

  // Parses pipeline-level identities and registers a token for each. Returns null when off/unconfigured/none
  // declared, so callers skip seeding and behavior is unchanged.
  public IdentityExecutionContext buildPipelineIdentityContext(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String processedYaml) {
    if (!pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_PIPELINE_IDENTITY)
        || !harnessIdClientService.isEnabled()) {
      return null;
    }
    Map<String, IdentitySpec> identities = parsePipelineIdentities(processedYaml);
    if (isEmpty(identities)) {
      // No pipeline-level identities, but the feature is on — seed an empty context so stage/step-level
      // identities are picked up by NodeIdentityCascadeHelper (which checks hasIdentityExecutionContext).
      return IdentityExecutionContext.newBuilder().build();
    }
    // §8: reject reserved identity names and cap cardinality before minting any tokens.
    IdentityValidator.validatePipelineIdentities(identities);

    IdentityExecutionContext.Builder ctxBuilder = IdentityExecutionContext.newBuilder();
    for (Map.Entry<String, IdentitySpec> e : identities.entrySet()) {
      String identityName = e.getKey();
      IdentitySpec spec = e.getValue();
      if (StringUtils.isBlank(identityName) || spec == null) {
        continue;
      }
      ctxBuilder.putIdentities(identityName,
          buildEntry(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, identityName, spec));
    }
    return ctxBuilder.build();
  }

  private IdentityEntry buildEntry(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identityName, IdentitySpec spec) {
    IdentityEntry.Builder entry = IdentityEntry.newBuilder()
                                      .setIdentityName(identityName)
                                      .setDeclaredAtLevel(IdentityDeclaredLevel.IDENTITY_LEVEL_PIPELINE);

    boolean disabled = DeclaredIdentityExtractor.toDisabledState(spec.getDisabled()) == DisabledState.DISABLED;
    entry.setDisabled(disabled);

    String audience = literalValue(spec.getAudience());
    if (StringUtils.isNotBlank(audience)) {
      entry.addAudiences(audience);
    }
    Map<String, String> claims = literalClaims(spec.getCustomClaims());
    if (!claims.isEmpty()) {
      entry.putAllCustomClaims(claims);
    }
    if (StringUtils.isNotBlank(spec.getSubjectTemplate())) {
      entry.setSubjectTemplate(spec.getSubjectTemplate());
    }

    // Disabled identities are recorded but never registered (no token is minted).
    if (disabled) {
      return entry.build();
    }

    try {
      WorkloadRegistrationRequest request =
          WorkloadRegistrationRequest.newBuilder()
              .setWorkloadType(WorkloadType.WORKLOAD_TYPE_PIPELINE)
              .setAccountId(StringUtils.defaultString(accountId))
              .setOrgId(StringUtils.defaultString(orgIdentifier))
              .setProjectId(StringUtils.defaultString(projectIdentifier))
              .setWorkloadContext(WorkloadContext.newBuilder().setPipeline(
                  PipelineContext.newBuilder().setPipelineId(StringUtils.defaultString(pipelineIdentifier)).build()))
              .setTtlSeconds(PIPELINE_WORKLOAD_TOKEN_TTL_SECONDS)
              .build();
      WorkloadRegistrationResponse response =
          PmsGrpcClientUtils.retryAndProcessException(harnessIdClientService::register, request);
      entry.setWorkloadToken(StringUtils.defaultString(response.getWorkloadToken()));
      if (response.hasExpiresAt()) {
        com.google.protobuf.Timestamp ts = response.getExpiresAt();
        entry.setWorkloadTokenExpiresAt(ts.getSeconds() * 1000L + ts.getNanos() / 1_000_000L);
      }
      if (StringUtils.isBlank(response.getWorkloadToken())) {
        metricService.incCounter(IDENTITY_NULL_TOKEN_METRIC);
      } else {
        metricService.incCounter(IDENTITY_REGISTER_SUCCESS_METRIC);
      }
    } catch (Exception ex) {
      // §4.8: never fail plan execution on one identity's registration failure; token left empty, never fabricated.
      log.error("Failed to register pipeline workload identity [{}] for pipeline [{}]; token left empty", identityName,
          pipelineIdentifier, ex);
      metricService.incCounter(IDENTITY_REGISTER_FAILURE_METRIC);
    }
    return entry.build();
  }

  private static String literalValue(ParameterField<String> field) {
    if (ParameterField.isBlank(field)) {
      return null;
    }
    Object value = field.fetchFinalValue();
    String str = value == null ? null : value.toString();
    // Drop unresolved expressions - they are resolved at the step/enrich leg with an ambiance.
    if (StringUtils.isBlank(str) || EngineExpressionEvaluator.hasExpressions(str)) {
      return null;
    }
    return str;
  }

  private static Map<String, String> literalClaims(Map<String, String> customClaims) {
    Map<String, String> resolved = new LinkedHashMap<>();
    if (isEmpty(customClaims)) {
      return resolved;
    }
    customClaims.forEach((key, value) -> {
      if (StringUtils.isNotBlank(value) && !EngineExpressionEvaluator.hasExpressions(value)) {
        resolved.put(key, value);
      }
    });
    // §8: never let a pipeline author spoof platform-controlled claims onto the token.
    return IdentityValidator.stripReservedClaims(resolved);
  }

  private Map<String, IdentitySpec> parsePipelineIdentities(String processedYaml) {
    Map<String, IdentitySpec> identities = new LinkedHashMap<>();
    if (StringUtils.isBlank(processedYaml)) {
      return identities;
    }
    try {
      YamlField pipelineField = YamlUtils.readTree(processedYaml).getNode().getField("pipeline");
      if (pipelineField == null) {
        return identities;
      }
      YamlField identitiesField = pipelineField.getNode().getField("identities");
      if (identitiesField == null) {
        return identities;
      }
      JsonNode identitiesNode = identitiesField.getNode().getCurrJsonNode();
      if (identitiesNode == null || !identitiesNode.isObject()) {
        return identities;
      }
      identitiesNode.fields().forEachRemaining(field -> {
        String key = field.getKey();
        // Skip the framework-injected __uuid key (added to every object node of the processed YAML).
        if (YamlNode.UUID_FIELD_NAME.equals(key)) {
          return;
        }
        JsonNode value = field.getValue();
        if (value == null || value.isNull() || !value.isObject()) {
          return;
        }
        IdentitySpec spec = YamlUtils.getMapper().convertValue(value, IdentitySpec.class);
        if (spec != null && spec.getCustomClaims() != null) {
          spec.getCustomClaims().remove(YamlNode.UUID_FIELD_NAME);
        }
        identities.put(key, spec);
      });
    } catch (Exception ex) {
      log.warn("Failed to parse pipeline-level identities from processed YAML; skipping pipeline identity"
              + " registration",
          ex);
    }
    return identities;
  }
}
