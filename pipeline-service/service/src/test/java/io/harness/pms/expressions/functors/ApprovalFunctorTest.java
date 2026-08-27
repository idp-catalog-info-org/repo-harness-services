/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.ANKUR_PATEL;
import static io.harness.rule.OwnerRule.AYUSHMAN;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.entities.ApprovalInstanceService;
import io.harness.steps.approval.step.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.harness.HarnessApprovalOutcome;
import io.harness.steps.approval.step.harness.beans.ApproversDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivity;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ApprovalFunctorTest extends CategoryTest {
  private static final String APPROVAL_NAME = "Admin";
  private static final String APPROVAL_EMAIL = "admin@harness.io";
  private static final String APPROVAL_COMMENT = "Approval comment";
  private static final String SECOND_APPROVAL_NAME = "Second Approver";
  private static final String SECOND_APPROVAL_EMAIL = "second@harness.io";
  private static final String SECOND_APPROVAL_COMMENT = "Second approval comment";
  private static final String PLAN_EXECUTION_ID = "execution_id";
  private static final String APPROVAL_INSTANCE_ID = "approval_instance_id";
  private static final String SECOND_APPROVAL_INSTANCE_ID = "second_approval_instance_id";
  @Mock private ApprovalInstanceService approvalInstanceService;
  @Mock private NodeExecutionService nodeExecutionService;
  @InjectMocks private ApprovalFunctor approvalFunctor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testBind() {
    on(approvalFunctor).set("planExecutionId", PLAN_EXECUTION_ID);
    when(approvalInstanceService.findAllHarnessApprovalInstancesByPlanExecutionId(PLAN_EXECUTION_ID))
        .thenReturn(List.of(HarnessApprovalInstance.builder()
                                .approvalMessage(APPROVAL_COMMENT)
                                .approvalActivities(List.of(
                                    HarnessApprovalActivity.builder()
                                        .comments(APPROVAL_COMMENT)
                                        .user(EmbeddedUser.builder().email(APPROVAL_EMAIL).name(APPROVAL_NAME).build())
                                        .build()))
                                .approvers(ApproversDTO.builder().userGroups(List.of(APPROVAL_NAME)).build())
                                .build()));

    Object resolvedObject = approvalFunctor.bind();
    assertThat(resolvedObject).isInstanceOf(HarnessApprovalOutcome.class);
    HarnessApprovalOutcome harnessApprovalOutcome = (HarnessApprovalOutcome) resolvedObject;
    assertThat(harnessApprovalOutcome.getApprovalActivities().get(0).getUser().getName()).isEqualTo(APPROVAL_NAME);
    assertThat(harnessApprovalOutcome.getApprovalActivities().get(0).getUser().getEmail()).isEqualTo(APPROVAL_EMAIL);
    assertThat(harnessApprovalOutcome.getApprovalActivities().get(0).getComments()).isEqualTo(APPROVAL_COMMENT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testBindReturnsNullWhenNoInstances() {
    on(approvalFunctor).set("planExecutionId", PLAN_EXECUTION_ID);
    when(approvalInstanceService.findAllHarnessApprovalInstancesByPlanExecutionId(PLAN_EXECUTION_ID))
        .thenReturn(Collections.emptyList());
    when(nodeExecutionService.fetchListOfApprovalInstanceIdsForPlanExecutionId(PLAN_EXECUTION_ID))
        .thenReturn(Collections.emptyList());

    assertThat(approvalFunctor.bind()).isNull();
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testBind_multipleSequentialApprovalSteps() {
    on(approvalFunctor).set("planExecutionId", PLAN_EXECUTION_ID);

    HarnessApprovalActivity firstActivity =
        HarnessApprovalActivity.builder()
            .comments(APPROVAL_COMMENT)
            .user(EmbeddedUser.builder().email(APPROVAL_EMAIL).name(APPROVAL_NAME).build())
            .approvedAt(1000L)
            .build();
    HarnessApprovalInstance firstApprovalInstance =
        HarnessApprovalInstance.builder()
            .approvalMessage(APPROVAL_COMMENT)
            .approvalActivities(List.of(firstActivity))
            .approvers(ApproversDTO.builder().userGroups(List.of(APPROVAL_NAME)).build())
            .build();
    firstApprovalInstance.setCreatedAt(1000L);

    HarnessApprovalActivity secondActivity =
        HarnessApprovalActivity.builder()
            .comments(SECOND_APPROVAL_COMMENT)
            .user(EmbeddedUser.builder().email(SECOND_APPROVAL_EMAIL).name(SECOND_APPROVAL_NAME).build())
            .approvedAt(2000L)
            .build();
    HarnessApprovalInstance secondApprovalInstance =
        HarnessApprovalInstance.builder()
            .approvalMessage(SECOND_APPROVAL_COMMENT)
            .approvalActivities(List.of(secondActivity))
            .approvers(ApproversDTO.builder().userGroups(List.of(SECOND_APPROVAL_NAME)).build())
            .build();
    secondApprovalInstance.setCreatedAt(2000L);

    when(approvalInstanceService.findAllHarnessApprovalInstancesByPlanExecutionId(PLAN_EXECUTION_ID))
        .thenReturn(List.of(firstApprovalInstance, secondApprovalInstance));

    Object resolvedObject = approvalFunctor.bind();
    assertThat(resolvedObject).isInstanceOf(HarnessApprovalOutcome.class);
    HarnessApprovalOutcome harnessApprovalOutcome = (HarnessApprovalOutcome) resolvedObject;
    assertThat(harnessApprovalOutcome.getApprovalActivities()).hasSize(2);
    assertThat(harnessApprovalOutcome.getApprovalActivities().get(0).getUser().getName()).isEqualTo(APPROVAL_NAME);
    assertThat(harnessApprovalOutcome.getApprovalActivities().get(0).getUser().getEmail()).isEqualTo(APPROVAL_EMAIL);
    assertThat(harnessApprovalOutcome.getApprovalActivities().get(1).getUser().getName())
        .isEqualTo(SECOND_APPROVAL_NAME);
    assertThat(harnessApprovalOutcome.getApprovalActivities().get(1).getUser().getEmail())
        .isEqualTo(SECOND_APPROVAL_EMAIL);
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testBind_skipsInstancesWithNullApprovalActivities() {
    on(approvalFunctor).set("planExecutionId", PLAN_EXECUTION_ID);

    HarnessApprovalInstance instanceWithNullActivities =
        HarnessApprovalInstance.builder()
            .approvalMessage(APPROVAL_COMMENT)
            .approvalActivities(null)
            .approvers(ApproversDTO.builder().userGroups(List.of(APPROVAL_NAME)).build())
            .build();
    instanceWithNullActivities.setCreatedAt(1000L);

    HarnessApprovalActivity secondActivity =
        HarnessApprovalActivity.builder()
            .comments(SECOND_APPROVAL_COMMENT)
            .user(EmbeddedUser.builder().email(SECOND_APPROVAL_EMAIL).name(SECOND_APPROVAL_NAME).build())
            .approvedAt(2000L)
            .build();
    HarnessApprovalInstance instanceWithActivities =
        HarnessApprovalInstance.builder()
            .approvalMessage(SECOND_APPROVAL_COMMENT)
            .approvalActivities(List.of(secondActivity))
            .approvers(ApproversDTO.builder().userGroups(List.of(SECOND_APPROVAL_NAME)).build())
            .build();
    instanceWithActivities.setCreatedAt(2000L);

    when(approvalInstanceService.findAllHarnessApprovalInstancesByPlanExecutionId(PLAN_EXECUTION_ID))
        .thenReturn(List.of(instanceWithNullActivities, instanceWithActivities));

    Object resolvedObject = approvalFunctor.bind();
    assertThat(resolvedObject).isInstanceOf(HarnessApprovalOutcome.class);
    HarnessApprovalOutcome harnessApprovalOutcome = (HarnessApprovalOutcome) resolvedObject;
    assertThat(harnessApprovalOutcome.getApprovalActivities()).hasSize(1);
    assertThat(harnessApprovalOutcome.getApprovalActivities().get(0).getUser().getName())
        .isEqualTo(SECOND_APPROVAL_NAME);
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testBind_v1YamlUsesV1Outcome() {
    on(approvalFunctor).set("planExecutionId", PLAN_EXECUTION_ID);

    HarnessApprovalActivity activity =
        HarnessApprovalActivity.builder()
            .comments(APPROVAL_COMMENT)
            .user(EmbeddedUser.builder().email(APPROVAL_EMAIL).name(APPROVAL_NAME).build())
            .approvedAt(1000L)
            .build();
    HarnessApprovalInstance v1ApprovalInstance =
        HarnessApprovalInstance.builder()
            .approvalMessage(APPROVAL_COMMENT)
            .approvalActivities(List.of(activity))
            .approvers(ApproversDTO.builder().userGroups(List.of(APPROVAL_NAME)).build())
            .build();
    v1ApprovalInstance.setCreatedAt(1000L);
    v1ApprovalInstance.setHarnessVersion(HarnessYamlVersion.V1);

    when(approvalInstanceService.findAllHarnessApprovalInstancesByPlanExecutionId(PLAN_EXECUTION_ID))
        .thenReturn(List.of(v1ApprovalInstance));

    Object resolvedObject = approvalFunctor.bind();
    assertThat(resolvedObject).isInstanceOf(io.harness.steps.approval.step.harness.v1.HarnessApprovalOutcome.class);
    io.harness.steps.approval.step.harness.v1.HarnessApprovalOutcome outcome =
        (io.harness.steps.approval.step.harness.v1.HarnessApprovalOutcome) resolvedObject;
    assertThat(outcome.getApprovalActivities()).hasSize(1);
    assertThat(outcome.getApprovalActivities().get(0).getUser().getName()).isEqualTo(APPROVAL_NAME);
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void testBindFallsBackToBypassedIdentityNodeApprovalInstanceOnRerun() {
    on(approvalFunctor).set("planExecutionId", PLAN_EXECUTION_ID);
    when(approvalInstanceService.findAllHarnessApprovalInstancesByPlanExecutionId(PLAN_EXECUTION_ID))
        .thenReturn(Collections.emptyList());
    when(nodeExecutionService.fetchListOfApprovalInstanceIdsForPlanExecutionId(PLAN_EXECUTION_ID))
        .thenReturn(List.of(APPROVAL_INSTANCE_ID));

    HarnessApprovalInstance originalApprovalInstance =
        HarnessApprovalInstance.builder()
            .approvalMessage(APPROVAL_COMMENT)
            .approvalActivities(
                List.of(HarnessApprovalActivity.builder()
                            .comments(APPROVAL_COMMENT)
                            .user(EmbeddedUser.builder().email(APPROVAL_EMAIL).name(APPROVAL_NAME).build())
                            .build()))
            .approvers(ApproversDTO.builder().userGroups(List.of(APPROVAL_NAME)).build())
            .build();
    originalApprovalInstance.setCreatedAt(1000L);
    when(approvalInstanceService.getApprovalInstancesByApprovalInstanceIds(
             PLAN_EXECUTION_ID, null, ApprovalType.HARNESS_APPROVAL, null, null, List.of(APPROVAL_INSTANCE_ID)))
        .thenReturn(List.of(originalApprovalInstance));

    Object resolvedObject = approvalFunctor.bind();
    assertThat(resolvedObject).isInstanceOf(HarnessApprovalOutcome.class);
    HarnessApprovalOutcome harnessApprovalOutcome = (HarnessApprovalOutcome) resolvedObject;
    assertThat(harnessApprovalOutcome.getApprovalActivities().get(0).getUser().getName()).isEqualTo(APPROVAL_NAME);
    assertThat(harnessApprovalOutcome.getApprovalActivities().get(0).getUser().getEmail()).isEqualTo(APPROVAL_EMAIL);
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testBindMergesActivitiesAcrossMultipleBypassedIdentityNodeApprovalInstancesOnRerun() {
    on(approvalFunctor).set("planExecutionId", PLAN_EXECUTION_ID);
    when(approvalInstanceService.findAllHarnessApprovalInstancesByPlanExecutionId(PLAN_EXECUTION_ID))
        .thenReturn(Collections.emptyList());
    when(nodeExecutionService.fetchListOfApprovalInstanceIdsForPlanExecutionId(PLAN_EXECUTION_ID))
        .thenReturn(List.of(APPROVAL_INSTANCE_ID, SECOND_APPROVAL_INSTANCE_ID));

    HarnessApprovalActivity firstActivity =
        HarnessApprovalActivity.builder()
            .comments(APPROVAL_COMMENT)
            .user(EmbeddedUser.builder().email(APPROVAL_EMAIL).name(APPROVAL_NAME).build())
            .build();
    HarnessApprovalInstance firstBypassedApprovalInstance =
        HarnessApprovalInstance.builder()
            .approvalMessage(APPROVAL_COMMENT)
            .approvalActivities(List.of(firstActivity))
            .approvers(ApproversDTO.builder().userGroups(List.of(APPROVAL_NAME)).build())
            .build();
    firstBypassedApprovalInstance.setCreatedAt(1000L);

    HarnessApprovalActivity secondActivity =
        HarnessApprovalActivity.builder()
            .comments(SECOND_APPROVAL_COMMENT)
            .user(EmbeddedUser.builder().email(SECOND_APPROVAL_EMAIL).name(SECOND_APPROVAL_NAME).build())
            .build();
    HarnessApprovalInstance secondBypassedApprovalInstance =
        HarnessApprovalInstance.builder()
            .approvalMessage(SECOND_APPROVAL_COMMENT)
            .approvalActivities(List.of(secondActivity))
            .approvers(ApproversDTO.builder().userGroups(List.of(SECOND_APPROVAL_NAME)).build())
            .build();
    secondBypassedApprovalInstance.setCreatedAt(2000L);

    when(approvalInstanceService.getApprovalInstancesByApprovalInstanceIds(PLAN_EXECUTION_ID, null,
             ApprovalType.HARNESS_APPROVAL, null, null, List.of(APPROVAL_INSTANCE_ID, SECOND_APPROVAL_INSTANCE_ID)))
        .thenReturn(List.of(secondBypassedApprovalInstance, firstBypassedApprovalInstance));

    Object resolvedObject = approvalFunctor.bind();
    assertThat(resolvedObject).isInstanceOf(HarnessApprovalOutcome.class);
    HarnessApprovalOutcome harnessApprovalOutcome = (HarnessApprovalOutcome) resolvedObject;
    assertThat(harnessApprovalOutcome.getApprovalActivities()).hasSize(2);
    assertThat(harnessApprovalOutcome.getApprovalActivities().get(0).getUser().getName()).isEqualTo(APPROVAL_NAME);
    assertThat(harnessApprovalOutcome.getApprovalActivities().get(0).getUser().getEmail()).isEqualTo(APPROVAL_EMAIL);
    assertThat(harnessApprovalOutcome.getApprovalActivities().get(1).getUser().getName())
        .isEqualTo(SECOND_APPROVAL_NAME);
    assertThat(harnessApprovalOutcome.getApprovalActivities().get(1).getUser().getEmail())
        .isEqualTo(SECOND_APPROVAL_EMAIL);
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void testBindReturnsNullWhenNoApprovalInstanceExistsEvenAfterFallback() {
    on(approvalFunctor).set("planExecutionId", PLAN_EXECUTION_ID);
    when(approvalInstanceService.findAllHarnessApprovalInstancesByPlanExecutionId(PLAN_EXECUTION_ID))
        .thenReturn(Collections.emptyList());
    when(nodeExecutionService.fetchListOfApprovalInstanceIdsForPlanExecutionId(PLAN_EXECUTION_ID))
        .thenReturn(Collections.emptyList());

    assertThat(approvalFunctor.bind()).isNull();
  }
}
