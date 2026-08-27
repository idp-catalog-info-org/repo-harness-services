/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.evaluator;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.DynamicSecretReferenceHelper;
import io.harness.engine.execution.ExecutionInputService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.expressions.functors.ExecutionSweepingOutputFunctor;
import io.harness.engine.expressions.functors.ExpandedJsonFunctor;
import io.harness.engine.expressions.functors.ExpandedJsonFunctorUtils;
import io.harness.engine.expressions.functors.ExpressionResolvedCheckFunctor;
import io.harness.engine.expressions.functors.NodeExecutionAncestorFunctor;
import io.harness.engine.expressions.functors.NodeExecutionChildFunctor;
import io.harness.engine.expressions.functors.NodeExecutionQualifiedFunctor;
import io.harness.engine.expressions.functors.OutcomeFunctor;
import io.harness.engine.expressions.functors.SecretFunctor;
import io.harness.engine.expressions.functors.SecretFunctorWithRbac;
import io.harness.engine.expressions.functors.SecretJsonFunctor;
import io.harness.engine.expressions.functors.type.NodeExecutionEntityType;
import io.harness.engine.expressions.metadata.ExecutionSweepingOutputMetadata;
import io.harness.engine.expressions.metadata.OutcomeMetadata;
import io.harness.engine.expressions.provider.ExpressionEvaluatorProvider;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.engine.secrets.ExpressionsObserverFactory;
import io.harness.exception.EngineExpressionEvaluationException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.expression.AutoCloseableExpressionTracker;
import io.harness.expression.ConnectorInputsFunctor;
import io.harness.expression.ConnectorInputsMapper;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.EngineExpressionEvaluatorResolver;
import io.harness.expression.EngineJexlContext;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.FeatureFlagFunctor;
import io.harness.expression.InputsFunctor;
import io.harness.expression.JsonFunctor;
import io.harness.expression.RegexFunctor;
import io.harness.expression.ResolveObjectResponse;
import io.harness.expression.SecureJexlDenialException;
import io.harness.expression.VariableResolverTracker;
import io.harness.expression.XmlFunctorWithNamespace;
import io.harness.expression.common.ExpressionConfig;
import io.harness.expression.common.ExpressionMode;
import io.harness.expression.functors.NGJsonFunctor;
import io.harness.expression.functors.NGShellScriptFunctor;
import io.harness.fme.FMEPipelineClient;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.ProcessorResult;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.serializer.SerializerUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.processor.ParameterDocumentFieldProcessor;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;
import io.harness.serializer.recaster.ParameterDocumentField;
import io.harness.serializer.recaster.ParameterDocumentFieldMapper;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.shell.ScriptType;
import io.harness.utils.PmsFeatureFlagService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * AmbianceExpressionEvaluator is the basic expression evaluator provided by the orchestration engine. It provides
 * support for expressions based on the runtime graph, outcomes and sweeping output. It contains other helpful
 * functors like regex, json and xml. Apart from this, it also supports static and group based aliases. All these
 * concepts are explained in detail here:
 * https://harness.atlassian.net/wiki/spaces/WR/pages/722536048/Expression+Evaluation.
 *
 * In order to add support for custom expressions/functors, users need to extend this class and override 2 methods -
 * {@link #initialize()} and {@link #fetchPrefixes()}. This subclass needs a corresponding {@link
 * ExpressionEvaluatorProvider} to be provided when adding a dependency on {@link io.harness.OrchestrationModule}. For a
 * sample implementation, look at SampleExpressionEvaluator.java and SampleExpressionEvaluatorProvider.java.
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_COMMON_STEPS, HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Getter
@Slf4j
public class AmbianceExpressionEvaluator extends EngineExpressionEvaluator {
  @Inject private PmsOutcomeService pmsOutcomeService;
  @Inject private PmsSweepingOutputService pmsSweepingOutputService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanExecutionService planExecutionService;
  @Inject(optional = true) @Nullable private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private PlanService planService;
  @Inject private InputSetValidatorFactory inputSetValidatorFactory;
  @Inject private NodeExecutionInfoService nodeExecutionInfoService;
  @Inject DynamicSecretReferenceHelper dynamicSecretReferenceHelper;
  @Inject(optional = true) @Nullable ConnectorInputsMapper connectorInputsMapper;
  @Inject FMEPipelineClient fmePipelineClient;

  @Inject private PlanExpansionService planExpansionService;

  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private PipelineRbacHelper pipelineRbacHelper;

  @Inject private ExpressionsObserverFactory expressionsObserverFactory;

  @Inject(optional = true) @Nullable protected MetricService metricService;

  protected final Ambiance ambiance;
  private final Set<NodeExecutionEntityType> entityTypes;
  private final boolean refObjectSpecific;
  private final Map<String, String> groupAliases;
  private final AutoCloseableExpressionTracker expressionTrackerCloseable;
  protected ExecutionSweepingOutputMetadata outputMetadata;
  protected OutcomeMetadata outcomeMetadata;

  protected NodeExecutionsCache nodeExecutionsCache;
  private final String SECRETS = "secrets";
  private final String SECRET_JSON = "secretJson";
  private static final String FME = "fme";

  private boolean contextMapProvided;
  @Inject private ExecutionInputService executionInputService;

  @Builder
  public AmbianceExpressionEvaluator(VariableResolverTracker variableResolverTracker, Ambiance ambiance,
      Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific, Map<String, Object> contextMap) {
    this(new AutoCloseableExpressionTracker(variableResolverTracker), ambiance, entityTypes, refObjectSpecific,
        contextMap, false);
  }

  @Builder
  public AmbianceExpressionEvaluator(AutoCloseableExpressionTracker expressionTracker, Ambiance ambiance,
      Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific, Map<String, Object> contextMap) {
    super(expressionTracker, expressionTracker.getVariableResolverTracker(), false);
    this.ambiance = ambiance;
    this.entityTypes = entityTypes == null ? NodeExecutionEntityType.allEntities() : entityTypes;
    this.refObjectSpecific = refObjectSpecific;
    this.groupAliases = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(contextMap)) {
      // TODO(REMOVE): ENABLED_FEATURE_FLAGS_KEY is not a real contextMap entry. This we added to pass the FF to
      // engineExpressionEvaluator.
      if (contextMap.size() > 1 || !contextMap.containsKey(EngineExpressionEvaluator.ENABLED_FEATURE_FLAGS_KEY)) {
        contextMapProvided = true;
      }
      contextMap.forEach(this::addToContext);
    }
    this.expressionTrackerCloseable = expressionTracker;
  }

  @Builder
  public AmbianceExpressionEvaluator(VariableResolverTracker variableResolverTracker, Ambiance ambiance,
      Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific, Map<String, Object> contextMap,
      boolean isCel) {
    this(new AutoCloseableExpressionTracker(variableResolverTracker), ambiance, entityTypes, refObjectSpecific,
        contextMap, isCel);
  }

  @Builder
  public AmbianceExpressionEvaluator(AutoCloseableExpressionTracker expressionTracker, Ambiance ambiance,
      Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific, Map<String, Object> contextMap,
      boolean isCel) {
    super(expressionTracker, expressionTracker.getVariableResolverTracker(), isCel);
    this.ambiance = ambiance;
    this.entityTypes = entityTypes == null ? NodeExecutionEntityType.allEntities() : entityTypes;
    this.refObjectSpecific = refObjectSpecific;
    this.groupAliases = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(contextMap)) {
      // TODO(REMOVE): ENABLED_FEATURE_FLAGS_KEY is not a real contextMap entry. This we added to pass the FF to
      // engineExpressionEvaluator.
      if (contextMap.size() > 1 || !contextMap.containsKey(EngineExpressionEvaluator.ENABLED_FEATURE_FLAGS_KEY)) {
        contextMapProvided = true;
      }
      contextMap.forEach(this::addToContext);
    }
    this.expressionTrackerCloseable = expressionTracker;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void initialize() {
    super.initialize();
    if (!refObjectSpecific) {
      // Add basic functors.
      addToContext("regex", new RegexFunctor());
      addToContext("shell", new NGShellScriptFunctor(ScriptType.BASH));
      // Todo(Archit): revisit NGJsonFunctor(PIE-9772)
      if (contextMapProvided) {
        addToContext("json", new JsonFunctor(getContextMap()));
      } else {
        addToContext("json", new NGJsonFunctor());
      }
      addToContext("xml", new XmlFunctorWithNamespace());

      if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())
          || AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIE_USE_SECRET_FUNCTOR_WITH_RBAC.name())) {
        addToContext(SECRETS,
            new SecretFunctorWithRbac(ambiance, pipelineRbacHelper,
                expressionsObserverFactory.getSubjectForSecretsRuntimeUsages(ExpressionsObserverFactory.SECRET),
                dynamicSecretReferenceHelper));
      } else {
        addToContext(SECRETS,
            new SecretFunctor(ambiance,
                expressionsObserverFactory.getSubjectForSecretsRuntimeUsages(ExpressionsObserverFactory.SECRET),
                pipelineRbacHelper, dynamicSecretReferenceHelper));
      }
      addToContext(SECRET_JSON, new SecretJsonFunctor());
    }

    nodeExecutionsCache = new NodeExecutionsCache(nodeExecutionService, planService, ambiance);
    /**
     * When resolving an expression for rollback mode execution,
     * the output name of the original execution is also needed.
     * This is to ensure that if possible the output should also be resolved via original execution.
     */

    String originalPlanExecutionIdForRollbackMode =
        ambiance.getMetadata() != null ? ambiance.getMetadata().getOriginalPlanExecutionIdForRollbackMode() : null;
    outcomeMetadata = new OutcomeMetadata(pmsOutcomeService, ambiance.getPlanExecutionId());
    outputMetadata = new ExecutionSweepingOutputMetadata(
        pmsSweepingOutputService, ambiance.getPlanExecutionId(), originalPlanExecutionIdForRollbackMode);
    if (entityTypes.contains(NodeExecutionEntityType.OUTCOME)) {
      addToContext("outcome",
          OutcomeFunctor.builder()
              .outcomeMetadata(outcomeMetadata)
              .ambiance(ambiance)
              .pmsOutcomeService(pmsOutcomeService)
              .metricService(metricService)
              .build());
    }

    if (entityTypes.contains(NodeExecutionEntityType.SWEEPING_OUTPUT)) {
      addToContext("output",
          ExecutionSweepingOutputFunctor.builder()
              .outputMetadata(outputMetadata)
              .pmsSweepingOutputService(pmsSweepingOutputService)
              .ambiance(ambiance)
              .nodeExecutionsCache(nodeExecutionsCache)
              .metricService(metricService)
              .build());
    }
    if (connectorInputsMapper != null) {
      addToContext("connectorInputs", new ConnectorInputsFunctor(connectorInputsMapper, ambiance));
    }

    addToContext(FME, new FeatureFlagFunctor(fmePipelineClient, ambiance));

    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      addToContext(YAMLFieldNameConstants.INPUTS,
          new InputsFunctor(null, getPipelineNodeWithOnlyInputs(), AmbianceUtils.getAccountId(ambiance),
              AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance),
              connectorInputsMapper));
    }
    addToContext("expression", ExpressionResolvedCheckFunctor.builder().build());
    // Access StepParameters and Outcomes of self and children.
    addToContext("child",
        NodeExecutionChildFunctor.builder()
            .nodeExecutionsCache(nodeExecutionsCache)
            .pmsOutcomeService(pmsOutcomeService)
            .pmsSweepingOutputService(pmsSweepingOutputService)
            .nodeExecutionInfoService(nodeExecutionInfoService)
            .planExecutionMetadataService(planExecutionMetadataService)
            .ambiance(ambiance)
            .entityTypes(entityTypes)
            .executionInputService(executionInputService)
            .outcomeMetadata(outcomeMetadata)
            .outputMetadata(outputMetadata)
            .harnessJexlEngine(getHarnessJexlEngine())
            .metricService(metricService)
            .planExecutionService(planExecutionService)
            .isCel(isCel)
            .build());
    // Access StepParameters and Outcomes of ancestors.
    addToContext("ancestor",
        NodeExecutionAncestorFunctor.builder()
            .nodeExecutionsCache(nodeExecutionsCache)
            .pmsOutcomeService(pmsOutcomeService)
            .pmsSweepingOutputService(pmsSweepingOutputService)
            .nodeExecutionInfoService(nodeExecutionInfoService)
            .planExecutionMetadataService(planExecutionMetadataService)
            .ambiance(ambiance)
            .entityTypes(entityTypes)
            .executionInputService(executionInputService)
            .groupAliases(groupAliases)
            .outcomeMetadata(outcomeMetadata)
            .outputMetadata(outputMetadata)
            .harnessJexlEngine(getHarnessJexlEngine())
            .metricService(metricService)
            .planExecutionService(planExecutionService)
            .isCel(isCel)
            .build());
    // Access StepParameters and Outcomes using fully qualified names.
    addToContext("qualified",
        NodeExecutionQualifiedFunctor.builder()
            .nodeExecutionsCache(nodeExecutionsCache)
            .pmsOutcomeService(pmsOutcomeService)
            .executionInputService(executionInputService)
            .pmsSweepingOutputService(pmsSweepingOutputService)
            .planExecutionMetadataService(planExecutionMetadataService)
            .nodeExecutionInfoService(nodeExecutionInfoService)
            .ambiance(ambiance)
            .entityTypes(entityTypes)
            .outcomeMetadata(outcomeMetadata)
            .outputMetadata(outputMetadata)
            .harnessJexlEngine(getHarnessJexlEngine())
            .metricService(metricService)
            .planExecutionService(planExecutionService)
            .isCel(isCel)
            .build());
  }

  private JsonNode getPipelineNodeWithOnlyInputs() {
    Optional<String> pipelineYamlOpt =
        planExecutionMetadataService.getYaml(AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId());
    if (pipelineYamlOpt.isPresent()) {
      JsonNode entityNode = YamlUtils.readAsJsonNode(pipelineYamlOpt.get());
      JsonNode pipelineNode = entityNode != null ? entityNode.get(YAMLFieldNameConstants.PIPELINE) : null;
      if (pipelineNode != null && pipelineNode.isObject()) {
        ObjectNode pipelineObjectNode = (ObjectNode) pipelineNode;
        JsonNode inputsNode = pipelineNode.get(YAMLFieldNameConstants.INPUTS);
        pipelineObjectNode.removeAll();
        if (inputsNode != null) {
          pipelineObjectNode.set(YAMLFieldNameConstants.INPUTS, inputsNode);
        }
      }
      return pipelineNode;
    }
    return null;
  }

  /**
   * Add a group alias. Any expression that starts with `aliasName` will be replaced by the identifier of the first
   * ancestor node with the given groupName. Should be called within the initialize method only.
   *
   * @param aliasName   the name of the alias
   * @param groupName the name of the group
   */
  protected void addGroupAlias(@NotNull String aliasName, @NotNull String groupName) {
    if (isInitialized()) {
      return;
    }
    if (!validAliasName(aliasName)) {
      throw new InvalidRequestException("Invalid alias: " + aliasName);
    }
    groupAliases.put(aliasName, groupName);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotEmpty
  protected List<String> fetchPrefixes() {
    ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
    if (entityTypes.contains(NodeExecutionEntityType.OUTCOME)) {
      listBuilder.add("outcome");
    }
    if (entityTypes.contains(NodeExecutionEntityType.SWEEPING_OUTPUT)) {
      listBuilder.add("output");
    }
    return listBuilder.add("child").add("ancestor").add("qualified").addAll(super.fetchPrefixes()).build();
  }

  @Override
  @Deprecated
  public Object resolve(Object o, boolean skipUnresolvedExpressionsCheck) {
    return resolve(o, calculateExpressionMode(skipUnresolvedExpressionsCheck));
  }

  @Override
  public Object resolve(Object o, ExpressionMode expressionMode) {
    try (AutoCloseableExpressionTracker ignored = this.expressionTrackerCloseable) {
      return resolveInternal(o, expressionMode, ExpressionConfig.builder().build());
    }
  }

  @Override
  public Object resolve(Object o, ExpressionMode expressionMode, ExpressionConfig expressionConfig) {
    try (AutoCloseableExpressionTracker ignored = expressionTrackerCloseable) {
      return resolveInternal(o, expressionMode, expressionConfig);
    }
  }

  @Override
  public Object resolveInternal(Object o, ExpressionMode expressionMode, ExpressionConfig expressionConfig) {
    return ExpressionEvaluatorUtils.updateExpressions(
        o, new AmbianceResolveFunctorImpl(this, inputSetValidatorFactory, expressionMode, expressionConfig));
  }

  public static class AmbianceResolveFunctorImpl extends ResolveFunctorImpl {
    private final ParameterDocumentFieldProcessor parameterDocumentFieldProcessor;
    private final ExpressionConfig expressionConfig;

    public AmbianceResolveFunctorImpl(AmbianceExpressionEvaluator expressionEvaluator,
        InputSetValidatorFactory inputSetValidatorFactory, ExpressionMode expressionMode,
        ExpressionConfig expressionConfig) {
      super(expressionEvaluator, expressionMode);
      this.parameterDocumentFieldProcessor = new ParameterDocumentFieldProcessor(
          new EngineExpressionEvaluatorResolver(getExpressionEvaluator()), inputSetValidatorFactory, expressionMode);
      this.expressionConfig = expressionConfig;
    }

    @Override
    public ExpressionConfig expressionConfig() {
      return expressionConfig;
    }

    @Override
    public ResolveObjectResponse processObject(Object o) {
      Optional<ParameterDocumentField> docFieldOptional = ParameterDocumentFieldMapper.fromParameterFieldMap(o);
      if (!docFieldOptional.isPresent()) {
        return new ResolveObjectResponse(false, null);
      }

      ParameterDocumentField docField = docFieldOptional.get();
      processObjectInternal(docField);

      Map<String, Object> map = (Map<String, Object>) o;
      SerializerUtils.setEncodedValue(map, RecastOrchestrationUtils.toMap(docField));
      return new ResolveObjectResponse(true, map);
    }

    private void processObjectInternal(ParameterDocumentField documentField) {
      ProcessorResult processorResult = parameterDocumentFieldProcessor.process(documentField);
      if (processorResult.isError()) {
        throw new EngineExpressionEvaluationException(processorResult.getMessage(), processorResult.getExpression());
      }
    }
  }

  @Override
  protected Object evaluatePrefixCombinations(
      String expressionBlock, EngineJexlContext ctx, int depth, ExpressionMode expressionMode) {
    try {
      // Currently we use RefObjectSpecific only when the call is from PmsOutcomeServiceImpl or
      // PmsSweepingOutputServiceImpl. We will use new functor if RefObjectSpecific is used because we need recast
      // additions in our map.
      if (!refObjectSpecific && AmbianceUtils.shouldUseExpressionEngineV2(ambiance)
          && canExpressionResolvedByV2(expressionBlock, ctx.getUnsupported())) {
        String normalizedExpression = applyStaticAliases(expressionBlock);
        // Apply all the prefixes and return first one that evaluates successfully.
        List<String> finalExpressions = fetchExpressionsV2(normalizedExpression);
        Object obj = ExpandedJsonFunctor.builder()
                         .planExpansionService(planExpansionService)
                         .nodeExecutionInfoService(nodeExecutionInfoService)
                         .ambiance(ambiance)
                         .groupAliases(groupAliases)
                         .metricService(metricService)
                         .build()
                         .asJson(finalExpressions);
        if (obj != null) {
          ctx.addToContext(Map.of("expandedJson", obj));
        }
        Object object = evaluateCombinations(normalizedExpression, finalExpressions, ctx, depth, expressionMode);

        // when "null" values returned by evaluateCombinations, in that case we want to evaluate the expressions from v1
        // expression engine.
        if (object != null && !object.toString().equals("null")) {
          return object;
        }
        log.info(String.format("Could not resolve via V2 expression engine: %s. Falling back to V1", expressionBlock));
        ctx.addToSet(expressionBlock);
      }
    } catch (Exception ex) {
      if (containsSecurityDenial(ex)) {
        log.error("Security denial in V2 expression evaluation: {}. Refusing fallback to V1.", expressionBlock, ex);
        throw ex;
      }
      ctx.addToSet(expressionBlock);
      log.info(
          String.format("Could not resolve via V2 expression engine: %s. Falling back to V1", expressionBlock), ex);
    }
    return super.evaluatePrefixCombinations(expressionBlock, ctx, depth, expressionMode);
  }

  private static boolean containsSecurityDenial(Throwable t) {
    for (Throwable cur = t; cur != null; cur = cur.getCause() == cur ? null : cur.getCause()) {
      if (cur instanceof SecureJexlDenialException) {
        return true;
      }
    }
    return false;
  }

  private List<String> fetchExpressionsV2(String normalizedExpression) {
    if (hasExpressions(normalizedExpression)) {
      return Collections.singletonList(normalizedExpression);
    }
    ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
    if (entityTypes.contains(NodeExecutionEntityType.OUTCOME)) {
      listBuilder.add(String.format("outcome.%s", normalizedExpression));
    }
    if (entityTypes.contains(NodeExecutionEntityType.SWEEPING_OUTPUT)) {
      listBuilder.add(String.format("output.%s", normalizedExpression));
    }
    listBuilder.addAll(ExpandedJsonFunctorUtils.getExpressions(ambiance, groupAliases, normalizedExpression));
    return listBuilder.build();
  }

  public boolean canExpressionResolvedByV2(String expression, Set<String> unsupported) {
    return true;
  }
}
