/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution;

import static io.harness.rule.OwnerRule.VEDANT;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.ENABLE_SSCA_AIRGAP;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.PIPELINE_TRIGGER_BY;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.PIPELINE_TRIGGER_TYPE;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.PLUGIN_ARTIFACT_SOURCE;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.PLUGIN_ARTIFACT_SOURCE_TYPE;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.PLUGIN_ATTESTED_EVENT_SCOPE;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.PLUGIN_ATTESTED_EVENT_TYPES;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.PLUGIN_BASE64_SECRET;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.PLUGIN_TYPE;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.PLUGIN_VERIFY_ATTESTATION_SIGNATURES;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.POLICY_FILE_IDENTIFIER;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.SSCA_MANAGER_ENABLED;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.STAGE_EXECUTION_ID;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.STAGE_NAME;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.STAGE_TYPE;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.STEP_EXECUTION_ID;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.STEP_ID;
import static io.harness.ssca.execution.enforceAttestation.SscaEnforceAttestationStepPluginUtils.STEP_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.ssca.beans.attestation.scope.AttestedEventScope;
import io.harness.ssca.beans.attestation.scope.AttestedEventScopeType;
import io.harness.ssca.beans.policy.EnforcementPolicy;
import io.harness.ssca.beans.source.DockerSbomSource;
import io.harness.ssca.beans.source.EcrSbomSource;
import io.harness.ssca.beans.source.ImageSbomSource;
import io.harness.ssca.beans.source.SbomSource;
import io.harness.ssca.beans.source.SbomSourceType;
import io.harness.ssca.beans.stepinfo.EnforceAttestationStepInfo;
import io.harness.ssca.beans.store.HarnessStore;
import io.harness.ssca.beans.store.PolicyStore;
import io.harness.ssca.beans.store.StoreType;
import io.harness.ssca.client.NgSettingsUtils;

import java.util.Arrays;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.SSCA)
public class EnforceAttestationPluginHelperTest extends CIExecutionTestBase {
  @InjectMocks private EnforceAttestationPluginHelper enforceAttestationPluginHelper;

  @Mock private SscaPluginUtils sscaPluginUtils;
  @Mock private NgSettingsUtils ngSettingsUtils;

  Ambiance ambiance = Ambiance.newBuilder()
                          .putSetupAbstractions("accountId", "accountId")
                          .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                          .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                          .addLevels(Level.newBuilder()
                                         .setRuntimeId("runtimeID")
                                         .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE)))
                          .build();

  @Before
  public void setup() {
    when(sscaPluginUtils.getStageName(any())).thenReturn("stageName");
    when(sscaPluginUtils.getStageExecutionIdentifier(any())).thenReturn("stageExecId");
    when(sscaPluginUtils.getStageType(any())).thenReturn("CI");
    when(sscaPluginUtils.getPipelineTriggerType(any())).thenReturn("MANUAL");
    when(sscaPluginUtils.getPipelineTriggerBy(any())).thenReturn("user");
    when(sscaPluginUtils.getPipelineTriggerByEmail(any())).thenReturn("user@harness.io");
    when(ngSettingsUtils.getBaseEncodingEnabled(anyString(), anyString(), anyString())).thenReturn(false);
    when(ngSettingsUtils.getAirgapEnabled(anyString(), anyString(), anyString())).thenReturn(false);
  }

  @Test
  @Owner(developers = VEDANT)
  @Category(UnitTests.class)
  public void testGetEnvVariables_withImageSource_andSpecificScope() {
    EnforceAttestationStepInfo stepInfo =
        EnforceAttestationStepInfo.builder()
            .name("enforce-step")
            .source(SbomSource.builder()
                        .type(SbomSourceType.IMAGE)
                        .sbomSourceSpec(ImageSbomSource.builder()
                                            .image(ParameterField.createValueField("myorg/myapp"))
                                            .connector(ParameterField.createValueField("docker_connector"))
                                            .build())
                        .build())
            .policy(EnforcementPolicy.builder()
                        .store(PolicyStore.builder()
                                   .type(StoreType.HARNESS)
                                   .storeSpec(HarnessStore.builder()
                                                  .file(ParameterField.createValueField("/policies/attest.rego"))
                                                  .build())
                                   .build())
                        .build())
            .verifyAttestationSignatures(ParameterField.createValueField(true))
            .attestedEventScope(AttestedEventScope.builder()
                                    .scope(AttestedEventScopeType.SPECIFIC)
                                    .eventTypes(ParameterField.createValueField(Arrays.asList("Build", "Security")))
                                    .build())
            .build();

    Map<String, String> envMap =
        enforceAttestationPluginHelper.getEnforceAttestationStepEnvVariables(stepInfo, "id1", ambiance, Type.K8);

    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap.get(PLUGIN_TYPE)).isEqualTo("EnforceAttestation");
    assertThat(envMap.get(SSCA_MANAGER_ENABLED)).isEqualTo("true");
    assertThat(envMap.get(PLUGIN_ARTIFACT_SOURCE_TYPE)).isEqualTo("image");
    assertThat(envMap.get(PLUGIN_ARTIFACT_SOURCE)).isEqualTo("myorg/myapp");
    assertThat(envMap.get(POLICY_FILE_IDENTIFIER)).isEqualTo("/policies/attest.rego");
    assertThat(envMap.get(PLUGIN_VERIFY_ATTESTATION_SIGNATURES)).isEqualTo("true");
    assertThat(envMap.get(PLUGIN_ATTESTED_EVENT_SCOPE)).isEqualTo("SPECIFIC");
    assertThat(envMap.get(PLUGIN_ATTESTED_EVENT_TYPES)).isEqualTo("Build,Security");
    assertThat(envMap.get(STEP_EXECUTION_ID)).isEqualTo("runtimeID");
    assertThat(envMap.get(STEP_ID)).isEqualTo("id1");
    assertThat(envMap.get(STEP_NAME)).isEqualTo("enforce-step");
    assertThat(envMap.get(STAGE_NAME)).isEqualTo("stageName");
    assertThat(envMap.get(STAGE_EXECUTION_ID)).isEqualTo("stageExecId");
    assertThat(envMap.get(STAGE_TYPE)).isEqualTo("CI");
    assertThat(envMap.get(PIPELINE_TRIGGER_TYPE)).isEqualTo("MANUAL");
    assertThat(envMap.get(PIPELINE_TRIGGER_BY)).isEqualTo("user");
    assertThat(envMap.get(PLUGIN_BASE64_SECRET)).isEqualTo("false");
    assertThat(envMap.get(ENABLE_SSCA_AIRGAP)).isEqualTo("false");
  }

  @Test
  @Owner(developers = VEDANT)
  @Category(UnitTests.class)
  public void testGetEnvVariables_withAllScope() {
    EnforceAttestationStepInfo stepInfo =
        EnforceAttestationStepInfo.builder()
            .name("enforce-all")
            .source(SbomSource.builder()
                        .type(SbomSourceType.DOCKER)
                        .sbomSourceSpec(DockerSbomSource.builder()
                                            .image(ParameterField.createValueField("docker.io/myapp"))
                                            .connector(ParameterField.createValueField("docker_conn"))
                                            .build())
                        .build())
            .policy(EnforcementPolicy.builder()
                        .store(PolicyStore.builder()
                                   .type(StoreType.HARNESS)
                                   .storeSpec(HarnessStore.builder()
                                                  .file(ParameterField.createValueField("/policies/all.rego"))
                                                  .build())
                                   .build())
                        .build())
            .verifyAttestationSignatures(ParameterField.createValueField(false))
            .attestedEventScope(AttestedEventScope.builder().scope(AttestedEventScopeType.ALL).build())
            .build();

    Map<String, String> envMap =
        enforceAttestationPluginHelper.getEnforceAttestationStepEnvVariables(stepInfo, "id2", ambiance, Type.K8);

    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap.get(PLUGIN_ARTIFACT_SOURCE_TYPE)).isEqualTo("docker");
    assertThat(envMap.get(PLUGIN_ARTIFACT_SOURCE)).isEqualTo("docker.io/myapp");
    assertThat(envMap.get(PLUGIN_VERIFY_ATTESTATION_SIGNATURES)).isEqualTo("false");
    assertThat(envMap.get(PLUGIN_ATTESTED_EVENT_SCOPE)).isEqualTo("ALL");
    assertThat(envMap).doesNotContainKey(PLUGIN_ATTESTED_EVENT_TYPES);
  }

  @Test
  @Owner(developers = VEDANT)
  @Category(UnitTests.class)
  public void testGetEnvVariables_noNullValues() {
    EnforceAttestationStepInfo stepInfo =
        EnforceAttestationStepInfo.builder()
            .name("enforce-clean")
            .source(SbomSource.builder()
                        .type(SbomSourceType.IMAGE)
                        .sbomSourceSpec(ImageSbomSource.builder()
                                            .image(ParameterField.createValueField("myimage"))
                                            .connector(ParameterField.createValueField("conn"))
                                            .build())
                        .build())
            .policy(EnforcementPolicy.builder()
                        .store(PolicyStore.builder()
                                   .type(StoreType.HARNESS)
                                   .storeSpec(
                                       HarnessStore.builder().file(ParameterField.createValueField("/p.rego")).build())
                                   .build())
                        .build())
            .verifyAttestationSignatures(ParameterField.createValueField(true))
            .attestedEventScope(AttestedEventScope.builder().scope(AttestedEventScopeType.ALL).build())
            .build();

    Map<String, String> envMap =
        enforceAttestationPluginHelper.getEnforceAttestationStepEnvVariables(stepInfo, "id3", ambiance, Type.K8);

    assertThat(envMap.values()).doesNotContainNull();
  }

  @Test
  @Owner(developers = VEDANT)
  @Category(UnitTests.class)
  public void testGetEnvVariables_withEcrSource() {
    EnforceAttestationStepInfo stepInfo =
        EnforceAttestationStepInfo.builder()
            .name("enforce-ecr")
            .source(SbomSource.builder()
                        .type(SbomSourceType.ECR)
                        .sbomSourceSpec(EcrSbomSource.builder()
                                            .image(ParameterField.createValueField("myapp"))
                                            .region(ParameterField.createValueField("us-east-1"))
                                            .account(ParameterField.createValueField("123456789"))
                                            .connector(ParameterField.createValueField("aws_connector"))
                                            .build())
                        .build())
            .policy(EnforcementPolicy.builder()
                        .store(PolicyStore.builder()
                                   .type(StoreType.HARNESS)
                                   .storeSpec(
                                       HarnessStore.builder().file(ParameterField.createValueField("/p.rego")).build())
                                   .build())
                        .build())
            .verifyAttestationSignatures(ParameterField.createValueField(true))
            .attestedEventScope(
                AttestedEventScope.builder()
                    .scope(AttestedEventScopeType.SPECIFIC)
                    .eventTypes(ParameterField.createValueField(Arrays.asList("Build", "Security", "Custom")))
                    .build())
            .build();

    Map<String, String> envMap =
        enforceAttestationPluginHelper.getEnforceAttestationStepEnvVariables(stepInfo, "id4", ambiance, Type.K8);

    assertThat(envMap.get(PLUGIN_ARTIFACT_SOURCE_TYPE)).isEqualTo("ecr");
    assertThat(envMap.get(PLUGIN_ARTIFACT_SOURCE)).isEqualTo("myapp");
    assertThat(envMap.get(PLUGIN_ATTESTED_EVENT_TYPES)).isEqualTo("Build,Security,Custom");
  }
}
