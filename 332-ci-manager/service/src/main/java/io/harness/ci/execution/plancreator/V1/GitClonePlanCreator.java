/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator.V1;

import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_NAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_MOUNT_PATH;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.CIStepInfo;
import io.harness.beans.steps.stepinfo.GitCloneStepInfoV1;
import io.harness.beans.steps.stepinfo.StepNodeV1;
import io.harness.beans.steps.stepinfo.Strategy;
import io.harness.beans.steps.v1.CloneRef;
import io.harness.beans.steps.v1.CloneType;
import io.harness.ci.plan.creator.step.v1.AbstractStepPlanCreatorV1;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.expression.ExpressionResolverUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.PRCloneStrategy;
import io.harness.yaml.extended.ci.codebase.spec.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.spec.CommitShaBuildSpec;
import io.harness.yaml.extended.ci.codebase.spec.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.spec.TagBuildSpec;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CI)
public class GitClonePlanCreator extends AbstractStepPlanCreatorV1 {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(CIStepInfoType.GIT_CLONE.getDisplayName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject KryoSerializer kryoSerializer;
  private static final String ONE_HOUR = "1h";

  public Pair<PlanCreationResponse, JsonNode> createPlan(PlanCreationContext ctx, CodeBase codebase, String childID) {
    // create GitCloneStepNode
    StepNodeV1 gitCloneStepNode = getStepNode(codebase);
    gitCloneStepNode.setChildNodeId(childID);
    // create JsonNode
    JsonNode jsonNode = getJsonNode(gitCloneStepNode);
    // create Plan node
    PlanCreationResponse planCreationResponse = super.createPlanForField(ctx, gitCloneStepNode);
    return Pair.of(planCreationResponse, jsonNode);
  }

  private JsonNode getJsonNode(StepNodeV1 stepNode) {
    try {
      String jsonString = JsonPipelineUtils.writeJsonString(stepNode);
      return JsonPipelineUtils.getMapper().readTree(jsonString);
    } catch (IOException e) {
      throw new CIStageExecutionException("Failed to create gitclone step", e);
    }
  }

  private StepNodeV1 getStepNode(CodeBase codeBase) {
    if (codeBase == null) {
      throw new CIStageExecutionException("Codebase is mandatory with enabled cloneCodebase flag");
    }
    GitCloneStepInfoV1 gitCloneStepInfoV1 =
        GitCloneStepInfoV1.builder()
            .repo(codeBase.getRepoName())
            .connector(codeBase.getConnectorRef())
            .depth(codeBase.getDepth())
            .insecure(toInsecure(codeBase.getSslVerify()))
            .id(GIT_CLONE_STEP_ID)
            .name(GIT_CLONE_STEP_NAME)
            .clonedir(codeBase.getCloneDirectory() != null && !ParameterField.isNull(codeBase.getCloneDirectory())
                    ? codeBase.getCloneDirectory()
                    : ParameterField.createValueField(STEP_MOUNT_PATH))
            .ref(toCloneRef(codeBase.getBuild()))
            .strategy(toCloneStrategy(codeBase.getPrCloneStrategy()))
            .lfs(codeBase.getLfs())
            .trace(codeBase.getDebug())
            .tags(codeBase.getFetchTags())
            .submodules(codeBase.getSubmoduleStrategy())
            .sparseCheckout(codeBase.getSparseCheckout())
            .preFetchCommand(codeBase.getPreFetchCommand())
            .persistCredentials(codeBase.getPersistCredentials())
            .resources(codeBase.getResources())
            .user(codeBase.getRunAsUser())
            .build();
    return StepNodeV1.builder()
        .id(GIT_CLONE_STEP_ID)
        .name(GIT_CLONE_STEP_NAME)
        .timeout(ParameterField.createValueField(Timeout.builder().timeoutString(ONE_HOUR).build()))
        .uuid(generateUuid())
        .clone(gitCloneStepInfoV1)
        .build();
  }

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet("clone");
  }

  @Override
  protected CIStepInfo getSpec(StepNodeV1 stepElementConfig) {
    return stepElementConfig.getClone();
  }

  @Override
  protected StepType getStepType() {
    return STEP_TYPE;
  }

  // CodeBase stores v0 sslVerify; GitCloneStepInfoV1 uses insecure (inverted).
  protected ParameterField<Boolean> toInsecure(ParameterField<Boolean> sslVerify) {
    if (sslVerify == null || sslVerify.getValue() == null) {
      return ParameterField.createValueField(false);
    }
    return ParameterField.createValueField(!sslVerify.getValue());
  }

  protected Strategy toCloneStrategy(ParameterField<PRCloneStrategy> prCloneStrategy) {
    if (ParameterField.isNull(prCloneStrategy) || prCloneStrategy.getValue() == null) {
      return null;
    }
    for (Strategy strategy : Strategy.values()) {
      if (strategy.toPRCloneStrategy() == prCloneStrategy.getValue()) {
        return strategy;
      }
    }
    return null;
  }

  ParameterField<CloneRef> toCloneRef(ParameterField<Build> buildParameterField) {
    if (ParameterField.isNull(buildParameterField)) {
      return null;
    }
    switch (buildParameterField.getValue().getType()) {
      case PR:
        return ParameterField.createValueField(
            CloneRef.builder()
                .type(CloneType.PR)
                .number(ParameterField.createValueField(ExpressionResolverUtils.resolveIntegerParameterFromString(
                    ((PRBuildSpec) buildParameterField.getValue().getSpec()).getNumber(), null)))
                .build());
      case TAG:
        return ParameterField.createValueField(
            CloneRef.builder()
                .type(CloneType.TAG)
                .name(((TagBuildSpec) buildParameterField.getValue().getSpec()).getTag())
                .build());
      case BRANCH:
        return ParameterField.createValueField(
            CloneRef.builder()
                .type(CloneType.BRANCH)
                .name(((BranchBuildSpec) buildParameterField.getValue().getSpec()).getBranch())
                .build());
      case COMMIT_SHA:
        return ParameterField.createValueField(
            CloneRef.builder()
                .type(CloneType.COMMIT)
                .sha(((CommitShaBuildSpec) buildParameterField.getValue().getSpec()).getCommitSha())
                .build());
      default:
        return null;
    }
  }

  @Override
  protected List<AdviserObtainment> getAdviserObtainments(PlanCreationContext ctx, StepNodeV1 stepNode) {
    List<AdviserObtainment> adviserObtainmentList = super.getAdviserObtainments(ctx, stepNode);
    if (isNotEmpty(stepNode.getChildNodeId())) {
      adviserObtainmentList.add(
          AdviserObtainment.newBuilder()
              .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STAGE.name()).build())
              .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                  NextStepAdviserParameters.builder().nextNodeId(stepNode.getChildNodeId()).build())))
              .build());
    }
    return adviserObtainmentList;
  }
}
