/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.workloadidentity;

import static io.harness.pms.contracts.steps.StepCategory.STAGE;
import static io.harness.pms.contracts.steps.StepCategory.STEP;
import static io.harness.rule.OwnerRule.RAGHAV_MURALI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.harness.harnessid.proto.workload.v1.StepContext;
import com.harness.harnessid.proto.workload.v1.WorkloadRegistrationRequest;
import com.harness.harnessid.proto.workload.v1.WorkloadType;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class WorkloadIdentityRequestMapperTest extends CategoryTest {
  private static final String ACCOUNT_ID = "acc1";
  private static final String ORG_ID = "org1";
  private static final String PROJECT_ID = "proj1";
  private static final String PIPELINE_ID = "pipe1";
  private static final String STAGE_ID = "stage1";
  private static final String STAGE_TYPE = "Deployment";
  private static final String STEP_ID = "shell1";
  private static final String STEP_TYPE = "ShellScript";

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testBuildStepRequestPopulatesAllFields() {
    WorkloadRegistrationRequest request = WorkloadIdentityRequestMapper.buildStepRequest(buildAmbiance());

    assertThat(request.getWorkloadType()).isEqualTo(WorkloadType.WORKLOAD_TYPE_STEP);
    assertThat(request.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(request.getOrgId()).isEqualTo(ORG_ID);
    assertThat(request.getProjectId()).isEqualTo(PROJECT_ID);

    StepContext step = request.getWorkloadContext().getStep();
    assertThat(step.getPipelineId()).isEqualTo(PIPELINE_ID);
    assertThat(step.getStageId()).isEqualTo(STAGE_ID);
    assertThat(step.getStageType()).isEqualTo(STAGE_TYPE);
    assertThat(step.getStepId()).isEqualTo(STEP_ID);
    assertThat(step.getStepType()).isEqualTo(STEP_TYPE);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testBuildStepRequestSetsSubjectTemplateAndCustomClaims() {
    WorkloadRegistrationRequest request = WorkloadIdentityRequestMapper.buildStepRequest(
        buildAmbiance(), "repo:org/repo:ref:refs/heads/main", ImmutableMap.of("environment", "prod"));

    assertThat(request.getSubTemplate()).isEqualTo("repo:org/repo:ref:refs/heads/main");
    assertThat(request.getCustomClaimsMap()).containsEntry("environment", "prod");
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testBuildStepRequestWithoutSubjectTemplateOrClaims() {
    WorkloadRegistrationRequest request = WorkloadIdentityRequestMapper.buildStepRequest(buildAmbiance(), null, null);

    assertThat(request.getSubTemplate()).isEmpty();
    assertThat(request.getCustomClaimsMap()).isEmpty();
  }

  private Ambiance buildAmbiance() {
    Level stageLevel = Level.newBuilder()
                           .setIdentifier(STAGE_ID)
                           .setGroup("STAGE")
                           .setStepType(StepType.newBuilder().setType(STAGE_TYPE).setStepCategory(STAGE).build())
                           .build();
    Level stepLevel = Level.newBuilder()
                          .setIdentifier(STEP_ID)
                          .setStepType(StepType.newBuilder().setType(STEP_TYPE).setStepCategory(STEP).build())
                          .build();
    return Ambiance.newBuilder()
        .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_ID).build())
        .putAllSetupAbstractions(
            ImmutableMap.of("accountId", ACCOUNT_ID, "orgIdentifier", ORG_ID, "projectIdentifier", PROJECT_ID))
        .addLevels(stageLevel)
        .addLevels(stepLevel)
        .build();
  }
}
