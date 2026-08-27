/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.expression.LateBindingValue;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstanceService;
import io.harness.steps.approval.step.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(HarnessTeam.CDP)
public class ApprovalFunctor implements LateBindingValue {
  private final String planExecutionId;
  private final ApprovalInstanceService approvalInstanceService;
  private final NodeExecutionService nodeExecutionService;

  public ApprovalFunctor(String planExecutionId, ApprovalInstanceService approvalInstanceService,
      NodeExecutionService nodeExecutionService) {
    this.planExecutionId = planExecutionId;
    this.approvalInstanceService = approvalInstanceService;
    this.nodeExecutionService = nodeExecutionService;
  }

  @Override
  public Object bind() {
    List<HarnessApprovalInstance> fetchedApprovalInstances =
        approvalInstanceService.findAllHarnessApprovalInstancesByPlanExecutionId(planExecutionId);

    if (isEmpty(fetchedApprovalInstances)) {
      fetchedApprovalInstances = findAllApprovalInstancesFromBypassedIdentityNodes();
    }

    if (isEmpty(fetchedApprovalInstances)) {
      return null;
    }

    // Sort defensively (on a mutable copy) so the merge order and "latest instance" pick below are
    // always correct regardless of which path supplied the instances.
    List<HarnessApprovalInstance> sortedApprovalInstances = new ArrayList<>(fetchedApprovalInstances);
    sortedApprovalInstances.sort(Comparator.comparing(HarnessApprovalInstance::getCreatedAt));

    List<HarnessApprovalActivity> mergedActivities = new ArrayList<>();
    for (HarnessApprovalInstance instance : sortedApprovalInstances) {
      if (instance.getApprovalActivities() != null) {
        mergedActivities.addAll(instance.getApprovalActivities());
      }
    }

    HarnessApprovalInstance latestApprovalInstance = sortedApprovalInstances.get(sortedApprovalInstances.size() - 1);
    return latestApprovalInstance.toHarnessApprovalBaseOutcome(mergedActivities);
  }

  private List<HarnessApprovalInstance> findAllApprovalInstancesFromBypassedIdentityNodes() {
    List<String> approvalInstanceIds =
        nodeExecutionService.fetchListOfApprovalInstanceIdsForPlanExecutionId(planExecutionId);
    if (isNotEmpty(approvalInstanceIds)) {
      List<ApprovalInstance> approvalInstances = approvalInstanceService.getApprovalInstancesByApprovalInstanceIds(
          planExecutionId, null, ApprovalType.HARNESS_APPROVAL, null, null, approvalInstanceIds);
      List<HarnessApprovalInstance> harnessApprovalInstances = new ArrayList<>();
      for (ApprovalInstance instance : approvalInstances) {
        if (instance instanceof HarnessApprovalInstance) {
          harnessApprovalInstances.add((HarnessApprovalInstance) instance);
        }
      }
      return harnessApprovalInstances;
    }
    return new ArrayList<>();
  }
}
