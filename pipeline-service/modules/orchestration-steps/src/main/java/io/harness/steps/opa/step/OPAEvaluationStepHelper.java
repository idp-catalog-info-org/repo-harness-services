/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.opa.step;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InternalServerErrorException;
import io.harness.expression.EngineExpressionService;
import io.harness.expression.common.ExpressionMode;
import io.harness.network.SafeHttpCall;
import io.harness.opaclient.OpaServiceClient;
import io.harness.opaclient.model.Evaluation;
import io.harness.opaclient.model.PolicySetData;
import io.harness.opaclient.model.SignedUrlPayload;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.security.PmsSecurityContextNoSideEffectsGuard;
import io.harness.secrets.evaluator.CIVmSecretEvaluator;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.ServiceTokenGenerator;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class OPAEvaluationStepHelper {
  @Inject private OpaServiceClient opaServiceClient;
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private ServiceTokenGenerator tokenGenerator;
  @Inject @Named("opaServiceBaseUrl") private String opaServiceBaseUrl;
  @Inject @Named("opaServiceSecret") private String opaServiceSecret;
  @Inject private PipelineExpressionHelper pipelineExpressionHelper;
  @Inject private PipelineServiceConfiguration pipelineServiceConfiguration;

  public static final String PLUGIN_POLICY_SET_ID = "PLUGIN_POLICY_SET_ID";
  public static final String PLUGIN_EVALUATION_ID = "PLUGIN_EVALUATION_ID";
  public static final String PLUGIN_OPA_SERVICE_URL = "PLUGIN_OPA_SERVICE_URL";
  // this also needs to be set
  public static final String PLUGIN_OPA_AUTH_TOKEN = "PLUGIN_OPA_AUTH_TOKEN";
  public static final String PLUGIN_GCS_PAYLOAD_SIGNED_URL = "PLUGIN_GCS_PAYLOAD_SIGNED_URL";
  public static final String PLUGIN_POLICY_DETAILS = "PLUGIN_POLICY_DETAILS";
  public static final String PLUGIN_ACCOUNT_ID = "PLUGIN_ACCOUNT_ID";
  public static final String PLUGIN_ORG_IDENTIFIER = "PLUGIN_ORG_IDENTIFIER";
  public static final String PLUGIN_PROJECT_IDENTIFIER = "PLUGIN_PROJECT_IDENTIFIER";

  // Per-secret env var prefix; delegate JEXL substitutes the expression to the decrypted value
  // and the OPA plugin's masker (mask.go) replaces it in the rendered policy output.
  public static final String PLUGIN_OPA_SECRET_PREFIX = "PLUGIN_OPA_SECRET_";

  // Env var the OPA plugin's masker reads to find names of env vars holding secrets. K8s sets it
  // pod-wide via lite-engine; VM does not, so the VM path populates it here.
  public static final String HARNESS_SECRETS_LIST = "HARNESS_SECRETS_LIST";

  /**
   * Fetches policy set data from OPA service
   *
   * @param ambiance Pipeline execution ambiance (for accountId)
   * @param policySetId Policy set identifier
   * @param policySetOrgId Org ID where the policy set exists (can be null for account-level)
   * @param policySetProjectId Project ID where the policy set exists (can be null for account/org-level)
   */
  public PolicySetData fetchPolicySet(
      Ambiance ambiance, String policySetId, String policySetOrgId, String policySetProjectId) {
    if (isEmpty(policySetId)) {
      throw new IllegalArgumentException("Policy Set ID cannot be empty");
    }

    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (isEmpty(accountId)) {
      throw new IllegalArgumentException("Account ID cannot be empty");
    }

    // Use policy set's orgId and projectId directly (can be null for account-level or org-level policy sets)
    // The API requires null values to fetch policy sets from the correct scope
    String orgId = isNotEmpty(policySetOrgId) ? policySetOrgId : null;
    String projectId = isNotEmpty(policySetProjectId) ? policySetProjectId : null;

    try {
      log.info("Fetching policy set {} for account {}, org {}, project {} (using policy set's scope)", policySetId,
          accountId, orgId, projectId);

      PolicySetData policySetData;
      try (PmsSecurityContextNoSideEffectsGuard ignore = new PmsSecurityContextNoSideEffectsGuard(ambiance)) {
        policySetData = SafeHttpCall.executeWithExceptions(
            opaServiceClient.findOpaPolicySet(policySetId, accountId, orgId, projectId));
      }

      if (policySetData == null) {
        throw new RuntimeException("Policy set data is null for policy set: " + policySetId);
      }

      if (policySetData.getPolicies() == null || policySetData.getPolicies().isEmpty()) {
        log.warn("Policy set {} has no policies", policySetId);
      }

      log.info("Fetched policy set {} with {} policies for account {}, org {}, project {}", policySetId,
          policySetData.getPolicies() != null ? policySetData.getPolicies().size() : 0, accountId, orgId, projectId);
      return policySetData;
    } catch (Exception ex) {
      log.error("Failed to fetch policy set {} for account {}, org {}, project {}", policySetId, accountId, orgId,
          projectId, ex);
      throw new RuntimeException("Failed to fetch policy set: " + policySetId, ex);
    }
  }

  public String getPayloadGcsSignedUrl(Ambiance ambiance, String evaluationId) {
    if (isEmpty(evaluationId)) {
      throw new IllegalArgumentException("Evaluation ID cannot be empty");
    }

    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (isEmpty(accountId)) {
      throw new IllegalArgumentException("Account ID cannot be empty");
    }

    try {
      log.info("Fetching GCS signed URL for evaluation {} for account {}", evaluationId, accountId);
      SignedUrlPayload signedUrlPayload;
      try (PmsSecurityContextNoSideEffectsGuard ignore = new PmsSecurityContextNoSideEffectsGuard(ambiance)) {
        signedUrlPayload =
            SafeHttpCall.executeWithExceptions(opaServiceClient.getPayloadSignedUrl(evaluationId, accountId));
      }
      if (signedUrlPayload == null) {
        throw new InternalServerErrorException("Signed URL payload is null for evaluation: " + evaluationId);
      }
      String gcsSignedUrl = signedUrlPayload.getSignedUrl();
      if (isEmpty(gcsSignedUrl)) {
        throw new InternalServerErrorException("GCS signed URL is empty for evaluation: " + evaluationId);
      }
      log.info("Fetched GCS signed URL for evaluation {} for account {}", evaluationId, accountId);
      return gcsSignedUrl;
    } catch (Exception ex) {
      log.error("Failed to get GCS signed URL for evaluation {} for account {}", evaluationId, accountId, ex);
      throw new InternalServerErrorException("Failed to get GCS signed URL for evaluation: " + evaluationId, ex);
    }
  }

  /**
   * Builds environment variables for the plugin step
   * All values must be String type (environment variables are always strings)
   *
   * @param ambiance Pipeline execution ambiance (for accountId)
   * @param policySetId Policy set identifier
   * @param evaluationId Evaluation ID
   * @param gcsSignedUrl GCS signed URL for payload
   * @param policyDetailsJson Policy details JSON
   * @param policySetOrgId Org ID where the policy set exists (can be null for account-level)
   * @param policySetProjectId Project ID where the policy set exists (can be null for account/org-level)
   */
  public Map<String, String> buildEnvironmentVariables(Ambiance ambiance, String policySetId, String evaluationId,
      String gcsSignedUrl, String policyDetailsJson, String policySetOrgId, String policySetProjectId) {
    Map<String, String> envVars = new HashMap<>();

    if (isEmpty(policySetId)) {
      throw new IllegalArgumentException("Policy Set ID is required");
    }

    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (isEmpty(accountId)) {
      throw new IllegalArgumentException("Account ID cannot be empty");
    }
    envVars.put(PLUGIN_ACCOUNT_ID, accountId);

    if (isNotEmpty(policySetOrgId)) {
      envVars.put(PLUGIN_ORG_IDENTIFIER, policySetOrgId);
    }

    if (isNotEmpty(policySetProjectId)) {
      envVars.put(PLUGIN_PROJECT_IDENTIFIER, policySetProjectId);
    }

    envVars.put(PLUGIN_POLICY_SET_ID, policySetId);

    if (isEmpty(evaluationId)) {
      throw new IllegalArgumentException("Evaluation ID is required");
    }
    envVars.put(PLUGIN_EVALUATION_ID, evaluationId);

    if (isEmpty(gcsSignedUrl)) {
      throw new IllegalArgumentException("GCS signed URL is required");
    }
    envVars.put(PLUGIN_GCS_PAYLOAD_SIGNED_URL, gcsSignedUrl);

    if (isEmpty(policyDetailsJson)) {
      throw new IllegalArgumentException("Policy details JSON is required");
    }
    envVars.put(PLUGIN_POLICY_DETAILS, policyDetailsJson);

    // Use PipelineExpressionHelper to get base URL with vanity URL support
    // Same pattern as used in PipelineExpressionHelper.generatePipelineUrl() and generateUrl()
    String baseUrl;
    if (isNotEmpty(pipelineServiceConfiguration.getPipelineServiceBaseUrl())) {
      try {
        baseUrl = pipelineExpressionHelper.getBaseUrlWithVanitySupport(accountId);
        log.debug("Computed OPA service base URL with vanity URL support: {}", baseUrl);
      } catch (Exception ex) {
        log.warn("Failed to compute base URL with vanity URL, falling back to configured base URL", ex);
        baseUrl = pipelineServiceConfiguration.getPipelineServiceBaseUrl();
      }
    } else {
      // Fallback: use opaServiceBaseUrl if pipelineServiceBaseUrl is not configured
      baseUrl = opaServiceBaseUrl;
      log.debug("Using OPA service base URL as fallback: {}", baseUrl);
    }

    if (isEmpty(baseUrl)) {
      log.warn("OPA Service Base URL is not configured, plugin may not be able to connect to OPA service");
    } else {
      envVars.put(PLUGIN_OPA_SERVICE_URL, baseUrl);
      log.debug("Added OPA service URL to plugin environment variables: {}", baseUrl);
    }

    // Add OPA service auth token (time-limited JWT token)
    String authorizationToken;
    try (PmsSecurityContextNoSideEffectsGuard ignore = new PmsSecurityContextNoSideEffectsGuard(ambiance)) {
      authorizationToken = tokenGenerator.getServiceTokenWithDuration(
          opaServiceSecret, Duration.ofHours(1), SecurityContextBuilder.getPrincipal());
    } catch (Exception ex) {
      throw new InternalServerErrorException("Failed to create OPA service token", ex);
    }
    if (isEmpty(authorizationToken)) {
      log.warn("OPA Service Token is not available, plugin may not be able to authenticate with OPA service");
    } else {
      envVars.put(PLUGIN_OPA_AUTH_TOKEN, authorizationToken);
      log.debug("Added OPA service auth token to plugin environment variables");
    }

    log.info("Built {} environment variables for plugin step", envVars.size());
    return envVars;
  }

  // useSingleQuotesForSecretRefs=true (VM): rewrites obtain("id",t) -> obtain('id',t) so JSON
  // encoding keeps a JEXL-parseable expression. K8s keeps double quotes (addon resolver regex).
  // Returned set is the secret expressions collected during the rewrite (empty for K8s).
  public Pair<String, Set<String>> convertPolicySetDataToJsonString(
      Ambiance ambiance, PolicySetData policySetData, boolean useSingleQuotesForSecretRefs) {
    if (policySetData == null) {
      throw new IllegalArgumentException("Policy set data cannot be null");
    }

    if (ambiance == null) {
      throw new IllegalArgumentException("Ambiance cannot be null for expression resolution");
    }

    try {
      // Resolve expressions in all string fields of the PolicySetData object recursively
      // This will resolve expressions in rego code and other string fields
      Object resolvedPolicySetData = engineExpressionService.resolve(
          ambiance, policySetData, ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED, new HashMap<>());

      Set<String> secretRefExpressions = Collections.emptySet();
      if (useSingleQuotesForSecretRefs && resolvedPolicySetData != null) {
        CIVmSecretEvaluator ciVmSecretEvaluator = CIVmSecretEvaluator.builder().build();
        secretRefExpressions = ciVmSecretEvaluator.resolve(resolvedPolicySetData, AmbianceUtils.getNgAccess(ambiance),
            ambiance.getExpressionFunctorToken(), true, false);
      }

      // Convert the resolved object to JSON string
      String jsonString = JsonPipelineUtils.getJsonString(resolvedPolicySetData);
      log.debug("Converted PolicySetData to JSON string with resolved expressions for policy set: {}",
          policySetData.getIdentifier());
      return Pair.of(jsonString, secretRefExpressions);
    } catch (Exception ex) {
      log.error("Failed to convert PolicySetData to JSON string with expression resolution", ex);
      throw new RuntimeException("Failed to convert PolicySetData to JSON string with expression resolution", ex);
    }
  }

  /**
   * Fetches evaluation ID from OPA service using planExecutionId (executionId).
   * This is called at runtime when evaluationId is not provided in step parameters.
   *
   * @param ambiance Pipeline execution ambiance
   * @param planExecutionId Plan execution ID (same as executionId in evaluations API)
   * @return Evaluation ID (the id field from the first evaluation matching executionId)
   */
  public String fetchEvaluationIdFromPlanExecutionId(Ambiance ambiance, String planExecutionId) {
    if (isEmpty(planExecutionId)) {
      throw new IllegalArgumentException("Plan Execution ID cannot be empty");
    }

    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);

    if (isEmpty(accountId)) {
      throw new IllegalArgumentException("Account ID cannot be empty");
    }

    try {
      log.info("Fetching evaluation ID for planExecutionId={}, accountId={}, orgId={}, projectId={}, type=pipeline, "
              + "action=onrun",
          planExecutionId, accountId, orgId, projectId);

      List<Evaluation> evaluations;
      try (PmsSecurityContextNoSideEffectsGuard ignore = new PmsSecurityContextNoSideEffectsGuard(ambiance)) {
        evaluations = SafeHttpCall.executeWithErrorMessage(opaServiceClient.listEvaluationsByExecutionId(
            accountId, orgId, projectId, planExecutionId, "pipeline", "onrun", 1));
      }

      if (isEmpty(evaluations)) {
        throw new InternalServerErrorException(
            String.format("No evaluation found for planExecutionId=%s, accountId=%s, orgId=%s, "
                    + "projectId=%s, type=pipeline, action=onrun. "
                    + "Evaluation should be created when OPA executes SAAS policies before delegate steps run.",
                planExecutionId, accountId, orgId, projectId));
      }

      Evaluation evaluation = evaluations.get(0);
      if (evaluation == null || evaluation.getId() == null) {
        throw new InternalServerErrorException(
            "Evaluation ID is null in response for planExecutionId: " + planExecutionId);
      }

      String evaluationId = String.valueOf(evaluation.getId());
      log.info("Fetched evaluation ID={} for planExecutionId={}", evaluationId, planExecutionId);
      return evaluationId;
    } catch (Exception ex) {
      log.error("Failed to fetch evaluation ID for planExecutionId={}, accountId={}, orgId={}, projectId={}",
          planExecutionId, accountId, orgId, projectId, ex);
      throw new InternalServerErrorException(
          String.format("Failed to fetch evaluation ID for planExecutionId=%s: %s", planExecutionId, ex.getMessage()),
          ex);
    }
  }
}
