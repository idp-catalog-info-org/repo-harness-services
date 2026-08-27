/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.ModuleType;
import io.harness.NGCommonEntityConstants;
import io.harness.account.services.AccountClient;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.CollectionUtils;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.engine.execution.ExecutionInputService;
import io.harness.engine.executions.blockExecutionMetadata.BlockExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.expressions.evaluator.AmbianceExpressionEvaluator;
import io.harness.engine.expressions.functors.RuntimeFunctorFactory;
import io.harness.engine.expressions.functors.StoFunctor;
import io.harness.engine.expressions.functors.StrategyFunctor;
import io.harness.engine.expressions.functors.type.NodeExecutionEntityType;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.expression.AutoCloseableExpressionTracker;
import io.harness.expression.VariableResolverTracker;
import io.harness.filestore.remote.FileStoreClient;
import io.harness.ngtriggers.expressions.functors.EventPayloadFunctor;
import io.harness.ngtriggers.expressions.functors.TriggerFunctor;
import io.harness.organization.remote.OrganizationClient;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.expression.RemoteFunctorServiceGrpc.RemoteFunctorServiceBlockingStub;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expressions.functors.AccountFunctor;
import io.harness.pms.expressions.functors.ApprovalFunctor;
import io.harness.pms.expressions.functors.ConfigFileFunctorV2;
import io.harness.pms.expressions.functors.DynamicExecutionTagsFunctor;
import io.harness.pms.expressions.functors.ExecutionInputExpressionFunctor;
import io.harness.pms.expressions.functors.ExportedVariablesFunctor;
import io.harness.pms.expressions.functors.FileStoreFunctorV2;
import io.harness.pms.expressions.functors.ImagePullSecretFunctorV2;
import io.harness.pms.expressions.functors.InputSetFunctor;
import io.harness.pms.expressions.functors.InstanceFunctorV2;
import io.harness.pms.expressions.functors.K8sReleaseFunctorV2;
import io.harness.pms.expressions.functors.NotificationFunctor;
import io.harness.pms.expressions.functors.OrgFunctor;
import io.harness.pms.expressions.functors.PipelineExecutionFunctor;
import io.harness.pms.expressions.functors.ProjectFunctor;
import io.harness.pms.expressions.functors.RemoteExpressionFunctor;
import io.harness.pms.expressions.functors.ServiceVariableOverridesFunctor;
import io.harness.pms.expressions.functors.StagesExpressionValuesFunctor;
import io.harness.pms.expressions.functors.StringFunctor;
import io.harness.pms.expressions.functors.UnifiedConfigFileFunctor;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.project.remote.ProjectClient;
import io.harness.steps.approval.step.entities.ApprovalInstanceService;
import io.harness.stoserviceclient.STOServiceUtils;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class PMSExpressionEvaluator extends AmbianceExpressionEvaluator {
  @Inject Map<ModuleType, RemoteFunctorServiceBlockingStub> remoteFunctorServiceBlockingStubMap;
  @Inject @Named("PRIVILEGED") private AccountClient accountClient;
  @Inject @Named("PRIVILEGED") private OrganizationClient organizationClient;
  @Inject @Named("PRIVILEGED") private ProjectClient projectClient;
  @Inject private FileStoreClient fileStoreClient;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private PMSExecutionService pmsExecutionService;
  @Inject PmsSdkInstanceService pmsSdkInstanceService;
  @Inject PipelineExpressionHelper pipelineExpressionHelper;
  @Inject ExecutionInputService executionInputService;

  @Inject PmsExecutionSummaryService pmsExecutionSummaryService;

  @Inject PmsEngineExpressionService pmsEngineExpressionService;
  @Inject ApprovalInstanceService approvalInstanceService;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject BlockExecutionMetadataService blockExecutionMetadataService;
  @Inject PipelineSettingsService pipelineSettingsService;
  @Inject PipelineRetentionService pipelineRetentionService;
  @Inject RuntimeFunctorFactory runtimeFunctorFactory;
  @Inject(optional = true) STOServiceUtils stoServiceUtils;
  @Inject PMSInputSetService pmsInputSetService;
  @Inject ScopeResolutionHelper scopeResolutionHelper;
  private final String PIPELINE_FUNCTOR = "pipeline";
  private final String STO_FUNCTOR = "sto";
  private final String NOTIFICATION_FUNCTOR = "notification";

  public PMSExpressionEvaluator(VariableResolverTracker variableResolverTracker, Ambiance ambiance,
      Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific, Map<String, Object> contextMap,
      boolean isCel) {
    super(variableResolverTracker, ambiance, entityTypes, refObjectSpecific, contextMap, isCel);
  }

  public PMSExpressionEvaluator(AutoCloseableExpressionTracker expressionTracker, Ambiance ambiance,
      Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific, Map<String, Object> contextMap,
      boolean isCel) {
    super(expressionTracker, ambiance, entityTypes, refObjectSpecific, contextMap, isCel);
  }

  @Override
  protected void initialize() {
    super.initialize();
    // NG access functors
    addToContext("account", new AccountFunctor(accountClient, ambiance));
    addToContext("org", new OrgFunctor(organizationClient, ambiance));
    addToContext("project", new ProjectFunctor(projectClient, ambiance));

    addToContext(PIPELINE_FUNCTOR,
        new PipelineExecutionFunctor(
            pmsExecutionService, pipelineExpressionHelper, planExecutionMetadataService, ambiance));
    addToContext("executionInput", new ExecutionInputExpressionFunctor(executionInputService, ambiance));

    addToContext(
        "strategy", new StrategyFunctor(ambiance, nodeExecutionsCache, getNodeExecutionInfoService(), metricService));
    addToContext("inputSet",
        new InputSetFunctor(planExecutionMetadataService, pmsExecutionService, pmsInputSetService,
            scopeResolutionHelper, ambiance, metricService));
    addToContext("stageExpressions",
        new StagesExpressionValuesFunctor(planExecutionMetadataService, ambiance, planExecutionService));
    // Notification functor
    addToContext(NOTIFICATION_FUNCTOR,
        new NotificationFunctor(ambiance, getContextMap(), nodeExecutionsCache.getNodeExecutionService()));

    // String utility functor for escaping and other helpers
    addToContext("string", new StringFunctor());

    addToContext(STO_FUNCTOR, new StoFunctor(ambiance, stoServiceUtils));

    // Trigger functors
    addToContext(SetupAbstractionKeys.eventPayload,
        new EventPayloadFunctor(ambiance, planExecutionMetadataService, planExecutionService));
    addToContext(
        SetupAbstractionKeys.trigger, new TriggerFunctor(ambiance, planExecutionMetadataService, planExecutionService));
    Map<String, PmsSdkInstance> cacheValueMap = pmsSdkInstanceService.getSdkInstanceCacheValue();
    cacheValueMap.values().forEach(e -> {
      for (Map.Entry<String, String> entry : CollectionUtils.emptyIfNull(e.getStaticAliases()).entrySet()) {
        addStaticAlias(entry.getKey(), entry.getValue());
      }
    });

    if (ambiance.hasMetadata() && HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      cacheValueMap.values().forEach(e -> {
        for (Map.Entry<String, String> entry : CollectionUtils.emptyIfNull(e.getStaticAliasesUnified()).entrySet()) {
          addStaticAlias(entry.getKey(), entry.getValue());
        }
      });
    }

    cacheValueMap.forEach((key, value) -> {
      for (String functorKey : CollectionUtils.emptyIfNull(value.getSdkFunctors())) {
        if (functorKey.equals(NGCommonEntityConstants.INSTANCE_FUNCTOR)
            && AmbianceUtils.checkIfFeatureFlagEnabled(
                ambiance, FeatureName.PIPE_MOVE_INSTANCE_FUNCTOR_TO_PIPELINE_SERVICE.name())) {
          continue;
        }

        if (functorKey.equals(NGCommonEntityConstants.FILE_STORE_FUNCTOR)) {
          continue;
        }

        if (functorKey.equals(NGCommonEntityConstants.CONFIG_FILE_FUNCTOR)) {
          continue;
        }

        if (functorKey.equals(K8sReleaseFunctorV2.KUBERNETES_RELEASE_FUNCTOR_NAME)
            && AmbianceUtils.checkIfFeatureFlagEnabled(
                ambiance, FeatureName.PIPE_MOVE_KUBERNETES_RELEASE_FUNCTOR.name())) {
          continue;
        }

        addToContext(functorKey,
            RemoteExpressionFunctor.builder()
                .ambiance(ambiance)
                .blockExecutionMetadataService(blockExecutionMetadataService)
                .remoteFunctorServiceBlockingStub(remoteFunctorServiceBlockingStubMap.get(ModuleType.fromString(key)))
                .functorKey(functorKey)
                .build());
      }
    });
    addToContext("serviceVariableOverrides", new ServiceVariableOverridesFunctor(ambiance, pmsEngineExpressionService));
    addToContext("approval",
        new ApprovalFunctor(ambiance.getPlanExecutionId(), approvalInstanceService, getNodeExecutionService()));
    addToContext(
        "exportedVariables", new ExportedVariablesFunctor(ambiance, executionSweepingOutputService, outputMetadata));

    addToContext(NGCommonEntityConstants.FILE_STORE_FUNCTOR,
        new FileStoreFunctorV2(fileStoreClient, ambiance, pipelineRetentionService, pipelineSettingsService, this, 10));

    addToContext(NGCommonEntityConstants.CONFIG_FILE_FUNCTOR,
        new ConfigFileFunctorV2(getPmsOutcomeService(), fileStoreClient, ambiance, this, 10));

    addToContext("executionTags",
        new DynamicExecutionTagsFunctor(
            ambiance.getPlanExecutionId(), pmsExecutionSummaryService, pmsExecutionService));

    if (AmbianceUtils.checkIfFeatureFlagEnabled(
            ambiance, FeatureName.PIPE_MOVE_INSTANCE_FUNCTOR_TO_PIPELINE_SERVICE.name())) {
      addToContext(NGCommonEntityConstants.INSTANCE_FUNCTOR,
          InstanceFunctorV2.builder()
              .ambiance(ambiance)
              .pmsSweepingOutputService(getPmsSweepingOutputService())
              .build());
    }

    if (AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_MOVE_KUBERNETES_RELEASE_FUNCTOR.name())) {
      addToContext(K8sReleaseFunctorV2.KUBERNETES_RELEASE_FUNCTOR_NAME, new K8sReleaseFunctorV2(ambiance));
    }

    if (HarnessYamlVersion.V1.equals(ambiance.getMetadata().getHarnessVersion())) {
      addToContext("runtime", runtimeFunctorFactory.getRuntimeFunctor(ambiance));
      addToContext(NGCommonEntityConstants.CONFIG_FILE_FUNCTOR,
          new UnifiedConfigFileFunctor(getPmsOutcomeService(), fileStoreClient, ambiance, this, 10));
      addToContext("imagePullSecret",
          ImagePullSecretFunctorV2.builder()
              .ambiance(ambiance)
              .pmsSweepingOutputService(getPmsSweepingOutputService())
              .build());
    }

    // Group aliases
    // TODO: Replace with step category
    addGroupAlias(YAMLFieldNameConstants.STAGE, StepOutcomeGroup.STAGE.name());
    addGroupAlias(YAMLFieldNameConstants.STEP, StepOutcomeGroup.STEP.name());
    addGroupAlias(YAMLFieldNameConstants.STEP_GROUP, StepCategory.STEP_GROUP.name());
    // TODO: handle stage group
    addGroupAlias(YAMLFieldNameConstants.GROUP, NGCommonUtilPlanCreationConstants.GROUP);
  }

  public boolean canExpressionResolvedByV2(String expression, Set<String> unsupported) {
    if (isNotEmpty(expression)) {
      if (AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.CDS_EXPRESSION_V2_OPTIMISATION.name())) {
        String[] exp = expression.split("\\.");
        /*
        Context Map contains all the defined functors which will get resolved from Expgression V1 engine except pipeline
        functor.
        */
        if (getContextMap().containsKey(exp[0]) && !exp[0].contains(PIPELINE_FUNCTOR)) {
          return false;
        }
      }
      return !unsupported.contains(expression);
    }
    return true;
  }
  @Override
  protected List<String> fetchPrefixes() {
    ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
    listBuilder.addAll(super.fetchPrefixes());
    if (ambiance.getMetadata().getIsStagesExpressionsProvided()) {
      listBuilder.add("stageExpressions");
    }
    return listBuilder.build();
  }
}
