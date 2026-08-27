/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK;
import static io.harness.beans.FeatureName.CDS_BLOCK_SENSITIVE_EXPRESSIONS;
import static io.harness.beans.FeatureName.CDS_CONTAINER_STEP_DELEGATE_SELECTOR_PRECEDENCE;
import static io.harness.beans.FeatureName.CDS_CONTAINER_STEP_GROUP_RUN_AS_USER_AND_PRIVILEGED_FIX;
import static io.harness.beans.FeatureName.CDS_DISABLE_FALLBACK_EXPRESSION_ENGINE;
import static io.harness.beans.FeatureName.CDS_EXPRESSION_V2_OPTIMISATION;
import static io.harness.beans.FeatureName.CDS_INPUT_YAML_IN_WEBHOOK_NOTIFICATION;
import static io.harness.beans.FeatureName.CDS_OIDC_AWS_SESSION_TAGS;
import static io.harness.beans.FeatureName.CDS_REMOVE_RESUME_EVENT_FOR_ASYNC_AND_ASYNCCHAIN_MODE;
import static io.harness.beans.FeatureName.CDS_SAVE_EXECUTION_EXPRESSIONS;
import static io.harness.beans.FeatureName.CDS_USE_PARENT_NODE_TO_GET_MODULE_INFO;
import static io.harness.beans.FeatureName.CDS_USE_SINGLE_QUOTES_IN_SECRET_FUNCTOR;
import static io.harness.beans.FeatureName.CDS_USE_SWEEPING_OUTPUT_SECRET_FUNCTOR_FOR_IMAGE_PULL_SECRET;
import static io.harness.beans.FeatureName.CI_REPLACE_EXPRESSION_VALUE_ONLY_IF_NOT_EQUAL_TO_EXPRESSION;
import static io.harness.beans.FeatureName.PIE_CONTAINER_STEP_ABORT_USE_UPSERT;
import static io.harness.beans.FeatureName.PIE_RETURN_FINAL_EXPRESSION_ON_ERROR;
import static io.harness.beans.FeatureName.PIE_SECRETS_OBSERVER;
import static io.harness.beans.FeatureName.PIE_SIMPLIFY_LOG_BASE_KEY;
import static io.harness.beans.FeatureName.PIE_USE_COMMON_SWEEPING_OUTPUT_LIB;
import static io.harness.beans.FeatureName.PIE_USE_SECRET_FUNCTOR_WITH_RBAC;
import static io.harness.beans.FeatureName.PIPE_CACHE_CURRENT_STATUS;
import static io.harness.beans.FeatureName.PIPE_DISABLE_ESCAPE_AMPERSAND_IN_STAGE_EXEC_URL;
import static io.harness.beans.FeatureName.PIPE_DISABLE_THROWING_ENGINE_EXPRESSION_EVALUATION_EXCEPTION;
import static io.harness.beans.FeatureName.PIPE_DO_RBAC_CHECK_ON_SECRETS_FOR_PATH_REFERENCE;
import static io.harness.beans.FeatureName.PIPE_ENABLE_MANUAL_STAGE_RUN;
import static io.harness.beans.FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION;
import static io.harness.beans.FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION_FOR_TRIGGER_EXECUTIONS;
import static io.harness.beans.FeatureName.PIPE_ENABLE_RESTRICTION_IN_TRIGGER_HEADERS;
import static io.harness.beans.FeatureName.PIPE_ENABLE_STRATEGY_FOR_CHAINED_PIPELINES;
import static io.harness.beans.FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE;
import static io.harness.beans.FeatureName.PIPE_FIX_ABORT_RACE_CONDITIONS;
import static io.harness.beans.FeatureName.PIPE_FIX_ORIGINAL_STAGE_EXECUTION_IN_AMBIANCE;
import static io.harness.beans.FeatureName.PIPE_FIX_RESOURCE_RESTRAINTS_FOR_RETRY_STEPS;
import static io.harness.beans.FeatureName.PIPE_MOVE_FILE_STORE_FUNCTOR_TO_PIPELINE_SERVICE;
import static io.harness.beans.FeatureName.PIPE_MOVE_INSTANCE_FUNCTOR_TO_PIPELINE_SERVICE;
import static io.harness.beans.FeatureName.PIPE_MOVE_KUBERNETES_RELEASE_FUNCTOR;
import static io.harness.beans.FeatureName.PIPE_OPTIMISE_EXPANDED_JSON_FUNCTOR;
import static io.harness.beans.FeatureName.PIPE_OPTIMISE_EXPORTED_VARIABLES_FUNCTOR;
import static io.harness.beans.FeatureName.PIPE_REMOVE_STRATEGY_METADATA_POPULATION;
import static io.harness.beans.FeatureName.PIPE_RESOLVE_TO_NULL_VALUE_BASED_ON_EXPRESSION_MODE;
import static io.harness.beans.FeatureName.PIPE_SHOULD_ENABLE_PMS_SDK_KAFKA_STREAMING;
import static io.harness.beans.FeatureName.PIPE_SKIP_MATRIX_LOOP_ON_ZERO_ITERATIONS;
import static io.harness.beans.FeatureName.PIPE_SKIP_NEXT_STEP_ON_PIPELINE_ROLLBACK;
import static io.harness.beans.FeatureName.PIPE_STEP_CONCURRENCY_ENABLED;
import static io.harness.beans.FeatureName.PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION;
import static io.harness.beans.FeatureName.PIPE_USE_CDC_BASED_GRAPH;
import static io.harness.beans.FeatureName.PIPE_USE_COUNTER_BASED_STEP_CONCURRENCY_GATE;
import static io.harness.beans.FeatureName.PIPE_ZTS_ENABLED;
import static io.harness.beans.FeatureName.PL_ENABLE_GRANULAR_CLAIMS_FOR_VAULT;
import static io.harness.beans.FeatureName.USE_COMPLETE_STEP_GROUP_ID;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class ExecutionHelperUtils {
  PmsFeatureFlagHelper featureFlagService;

  // Add all FFs to this list that we want to use during pipeline execution
  public static final List<FeatureName> featureNames = List.of(CDS_DISABLE_FALLBACK_EXPRESSION_ENGINE,
      PIE_SIMPLIFY_LOG_BASE_KEY, CDS_REMOVE_RESUME_EVENT_FOR_ASYNC_AND_ASYNCCHAIN_MODE, PIE_SECRETS_OBSERVER,
      CDS_INPUT_YAML_IN_WEBHOOK_NOTIFICATION, CDS_BLOCK_SENSITIVE_EXPRESSIONS,
      CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK, CDS_USE_SINGLE_QUOTES_IN_SECRET_FUNCTOR,
      CDS_CONTAINER_STEP_DELEGATE_SELECTOR_PRECEDENCE, CDS_SAVE_EXECUTION_EXPRESSIONS,
      CDS_CONTAINER_STEP_GROUP_RUN_AS_USER_AND_PRIVILEGED_FIX, CDS_EXPRESSION_V2_OPTIMISATION,
      PIE_USE_SECRET_FUNCTOR_WITH_RBAC, PIPE_MOVE_FILE_STORE_FUNCTOR_TO_PIPELINE_SERVICE,
      PIE_RETURN_FINAL_EXPRESSION_ON_ERROR, PIE_USE_COMMON_SWEEPING_OUTPUT_LIB, PIPE_FIX_ABORT_RACE_CONDITIONS,
      CI_REPLACE_EXPRESSION_VALUE_ONLY_IF_NOT_EQUAL_TO_EXPRESSION, PIPE_MOVE_KUBERNETES_RELEASE_FUNCTOR,
      PIPE_MOVE_INSTANCE_FUNCTOR_TO_PIPELINE_SERVICE, USE_COMPLETE_STEP_GROUP_ID,
      PIPE_RESOLVE_TO_NULL_VALUE_BASED_ON_EXPRESSION_MODE, PIPE_REMOVE_STRATEGY_METADATA_POPULATION,
      PIPE_SHOULD_ENABLE_PMS_SDK_KAFKA_STREAMING, PIPE_ENABLE_RESTRICTION_IN_TRIGGER_HEADERS,
      CDS_USE_SWEEPING_OUTPUT_SECRET_FUNCTOR_FOR_IMAGE_PULL_SECRET, PIPE_DO_RBAC_CHECK_ON_SECRETS_FOR_PATH_REFERENCE,
      PIPE_OPTIMISE_EXPORTED_VARIABLES_FUNCTOR, PIPE_STEP_CONCURRENCY_ENABLED, PIPE_EXECUTION_SWITCH_FIELD_SOURCE,
      PIPE_OPTIMISE_EXPANDED_JSON_FUNCTOR, PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION,
      PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION_FOR_TRIGGER_EXECUTIONS, CDS_USE_PARENT_NODE_TO_GET_MODULE_INFO,
      PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION, PIPE_ENABLE_MANUAL_STAGE_RUN,
      PIPE_FIX_RESOURCE_RESTRAINTS_FOR_RETRY_STEPS, PIPE_FIX_ORIGINAL_STAGE_EXECUTION_IN_AMBIANCE,
      PL_ENABLE_GRANULAR_CLAIMS_FOR_VAULT, CDS_OIDC_AWS_SESSION_TAGS, PIPE_ZTS_ENABLED,
      PIPE_DISABLE_THROWING_ENGINE_EXPRESSION_EVALUATION_EXCEPTION, PIPE_CACHE_CURRENT_STATUS, PIPE_USE_CDC_BASED_GRAPH,
      PIPE_DISABLE_ESCAPE_AMPERSAND_IN_STAGE_EXEC_URL, PIE_CONTAINER_STEP_ABORT_USE_UPSERT,
      PIPE_ENABLE_STRATEGY_FOR_CHAINED_PIPELINES, PIPE_SKIP_MATRIX_LOOP_ON_ZERO_ITERATIONS,
      PIPE_USE_COUNTER_BASED_STEP_CONCURRENCY_GATE, PIPE_SKIP_NEXT_STEP_ON_PIPELINE_ROLLBACK);

  public void updateFeatureFlagsInExecutionMetadataBuilder(
      String accountIdentifier, ExecutionMetadata.Builder builder) {
    boolean isV1 = HarnessYamlVersion.isV1(builder.getHarnessVersion());
    for (FeatureName featureName : featureNames) {
      boolean isEnabled = featureFlagService.isEnabled(accountIdentifier, featureName)
          || (isV1 && PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION.equals(featureName));
      if (isEnabled) {
        builder.putFeatureFlagToValueMap(featureName.name(), isEnabled);
      }
    }
  }
}
