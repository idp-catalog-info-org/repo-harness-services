/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import io.harness.plancreator.steps.http.HttpStepInfo;
import io.harness.plancreator.steps.internal.AisreCreateAlertStepInfo;
import io.harness.plancreator.steps.internal.AisreCreateAlertStepNode;
import io.harness.plancreator.steps.internal.AisreCreateIncidentStepInfo;
import io.harness.plancreator.steps.internal.AisreCreateIncidentStepNode;
import io.harness.plancreator.steps.internal.ChangeAdvisorStepInfo;
import io.harness.plancreator.steps.internal.ChangeAdvisorStepNode;
import io.harness.plancreator.steps.internal.FmeFlagAddRemoveTargetsStepInfo;
import io.harness.plancreator.steps.internal.FmeFlagAddRemoveTargetsStepNode;
import io.harness.plancreator.steps.internal.FmeFlagArchiveStepInfo;
import io.harness.plancreator.steps.internal.FmeFlagArchiveStepNode;
import io.harness.plancreator.steps.internal.FmeFlagCreateInfo;
import io.harness.plancreator.steps.internal.FmeFlagCreateNode;
import io.harness.plancreator.steps.internal.FmeFlagKillStepInfo;
import io.harness.plancreator.steps.internal.FmeFlagKillStepNode;
import io.harness.plancreator.steps.internal.FmeFlagSetTargetsInfo;
import io.harness.plancreator.steps.internal.FmeFlagSetTargetsNode;
import io.harness.plancreator.steps.internal.FmeFlagUpdateInfo;
import io.harness.plancreator.steps.internal.FmeFlagUpdateNode;
import io.harness.plancreator.steps.internal.FmeMetricCheckStepInfo;
import io.harness.plancreator.steps.internal.FmeMetricCheckStepNode;
import io.harness.plancreator.steps.internal.RONotifyStepInfo;
import io.harness.plancreator.steps.internal.RONotifyStepNode;
import io.harness.serializer.KryoRegistrar;
import io.harness.steps.aisre.AisreCreateAlertOutcome;
import io.harness.steps.aisre.AisreCreateAlertStepParameters;
import io.harness.steps.aisre.AisreCreateIncidentOutcome;
import io.harness.steps.aisre.AisreCreateIncidentStepParameters;
import io.harness.steps.approval.step.custom.CustomApprovalOutcome;
import io.harness.steps.approval.step.custom.CustomApprovalStepInfo;
import io.harness.steps.approval.step.harness.HarnessApprovalOutcome;
import io.harness.steps.approval.step.jira.JiraApprovalOutcome;
import io.harness.steps.barriers.BarrierSpecParameters;
import io.harness.steps.barriers.beans.BarrierOutcome;
import io.harness.steps.cf.FlagConfigurationStepParameters;
import io.harness.steps.changeadvisor.ChangeAdvisorStepSpecParameters;
import io.harness.steps.fme.Allocation;
import io.harness.steps.fme.FmeFailureCriteria;
import io.harness.steps.fme.FmeFailureCriteriaSpec;
import io.harness.steps.fme.FmeFlagAddRemoveTargetsStepParameters;
import io.harness.steps.fme.FmeFlagArchiveStepParameters;
import io.harness.steps.fme.FmeFlagCreateParameters;
import io.harness.steps.fme.FmeFlagDefaultAllocationStepParameters;
import io.harness.steps.fme.FmeFlagKillStepParameters;
import io.harness.steps.fme.FmeFlagSetTargetsParameters;
import io.harness.steps.fme.FmeFlagUpdateParameters;
import io.harness.steps.fme.FmeMetricCheckOutcome;
import io.harness.steps.fme.FmeMetricCheckResponseData;
import io.harness.steps.fme.FmeMetricCheckStepParameters;
import io.harness.steps.fme.FmeMetricRef;
import io.harness.steps.fme.FmeMetricResultOutcome;
import io.harness.steps.fme.FmeTreatmentMetricOutcome;
import io.harness.steps.fme.Target;
import io.harness.steps.fme.TreatmentTarget;
import io.harness.steps.http.HttpOutcome;
import io.harness.steps.jira.JiraIssueOutcome;
import io.harness.steps.plugin.infrastructure.volumes.ContainerVolume;
import io.harness.steps.plugin.infrastructure.volumes.EmptyDirYaml;
import io.harness.steps.plugin.infrastructure.volumes.HostPathYaml;
import io.harness.steps.plugin.infrastructure.volumes.PersistentVolumeClaimYaml;
import io.harness.steps.resourcerestraint.AcquireMode;
import io.harness.steps.resourcerestraint.HoldingScope;
import io.harness.steps.resourcerestraint.ResourceRestraintSpecParameters;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintOutcome;
import io.harness.steps.ro.RONotifyStepParameters;
import io.harness.steps.shellscript.ShellScriptStepInfo;

import com.esotericsoftware.kryo.Kryo;

public class OrchestrationStepsContractKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    // moved from src layer to contracts
    kryo.register(BarrierOutcome.class, 3203);
    kryo.register(BarrierSpecParameters.class, 3204);
    kryo.register(ResourceRestraintSpecParameters.class, 3206);
    kryo.register(ResourceRestraintOutcome.class, 3207);
    kryo.register(AcquireMode.class, 3208);
    kryo.register(HoldingScope.class, 3210);

    kryo.register(HarnessApprovalOutcome.class, 3221);
    kryo.register(JiraApprovalOutcome.class, 3224);
    kryo.register(JiraIssueOutcome.class, 3225);
    kryo.register(FlagConfigurationStepParameters.class, 3226);
    kryo.register(CustomApprovalStepInfo.class, 3229);
    kryo.register(CustomApprovalOutcome.class, 3231);

    // made it same as which was in CD
    kryo.register(HttpStepInfo.class, 8048);
    kryo.register(HttpOutcome.class, 12501);
    kryo.register(ShellScriptStepInfo.class, 8055);

    kryo.register(ContainerVolume.class, 390100);
    kryo.register(EmptyDirYaml.class, 390103);
    kryo.register(HostPathYaml.class, 390101);
    kryo.register(PersistentVolumeClaimYaml.class, 390102);

    // FME classes
    kryo.register(Allocation.class, 390104);
    kryo.register(Target.class, 390105);
    kryo.register(TreatmentTarget.class, 390106);
    kryo.register(FmeFlagCreateParameters.class, 390107);
    kryo.register(FmeFlagUpdateParameters.class, 390108);
    kryo.register(FmeFlagAddRemoveTargetsStepParameters.class, 390109);
    kryo.register(FmeFlagSetTargetsParameters.class, 390110);
    kryo.register(FmeFlagDefaultAllocationStepParameters.class, 390111);
    kryo.register(FmeFlagKillStepParameters.class, 390112);
    kryo.register(FmeFlagArchiveStepParameters.class, 390113);
    kryo.register(FmeFlagCreateInfo.class, 390116);
    kryo.register(FmeFlagCreateNode.class, 390117);
    kryo.register(FmeFlagUpdateInfo.class, 390118);
    kryo.register(FmeFlagUpdateNode.class, 390119);
    kryo.register(FmeFlagAddRemoveTargetsStepInfo.class, 390120);
    kryo.register(FmeFlagAddRemoveTargetsStepNode.class, 390121);
    kryo.register(FmeFlagSetTargetsInfo.class, 390122);
    kryo.register(FmeFlagSetTargetsNode.class, 390123);
    kryo.register(FmeFlagKillStepInfo.class, 390126);
    kryo.register(FmeFlagKillStepNode.class, 390127);
    kryo.register(FmeFlagArchiveStepInfo.class, 390128);
    kryo.register(FmeFlagArchiveStepNode.class, 390129);
    kryo.register(RONotifyStepInfo.class, 390130);
    kryo.register(RONotifyStepNode.class, 390131);
    kryo.register(RONotifyStepParameters.class, 390132);

    // FME Metric Check classes
    kryo.register(FmeMetricCheckResponseData.class, 390133);
    kryo.register(FmeMetricCheckStepParameters.class, 390134);
    kryo.register(FmeMetricCheckStepInfo.class, 390135);
    kryo.register(FmeMetricCheckStepNode.class, 390136);
    kryo.register(FmeMetricCheckOutcome.class, 390137);
    kryo.register(FmeMetricResultOutcome.class, 390138);
    kryo.register(FmeTreatmentMetricOutcome.class, 390139);
    kryo.register(FmeFailureCriteria.class, 390140);
    kryo.register(FmeFailureCriteriaSpec.class, 390141);
    kryo.register(FmeMetricRef.class, 390142);

    // Change Advisor classes
    kryo.register(ChangeAdvisorStepSpecParameters.class, 390143);
    kryo.register(ChangeAdvisorStepInfo.class, 390144);
    kryo.register(ChangeAdvisorStepNode.class, 390145);

    kryo.register(AisreCreateIncidentStepParameters.class, 390146);
    kryo.register(AisreCreateIncidentOutcome.class, 390147);
    kryo.register(AisreCreateIncidentStepInfo.class, 390148);
    kryo.register(AisreCreateIncidentStepNode.class, 390149);
    kryo.register(AisreCreateAlertStepParameters.class, 390150);
    kryo.register(AisreCreateAlertOutcome.class, 390151);
    kryo.register(AisreCreateAlertStepInfo.class, 390152);
    kryo.register(AisreCreateAlertStepNode.class, 390153);
  }
}
