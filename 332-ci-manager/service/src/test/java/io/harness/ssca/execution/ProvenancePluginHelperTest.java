/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveGenericListParameter;
import static io.harness.rule.OwnerRule.AKSHAY_PANDEY;
import static io.harness.rule.OwnerRule.HUMANSHU_ARORA;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SHASHWAT_SACHAN;
import static io.harness.slsa.execution.provenance.ProvenanceStepPluginUtils.SSCA_SLSA_GENERATION_COSIGN_PASSWORD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.execution.artifact.ProvenanceArtifact;
import io.harness.beans.provenance.BuildDefinition;
import io.harness.beans.provenance.InternalParameters;
import io.harness.beans.provenance.Metadata;
import io.harness.beans.provenance.ProvenanceBuilder;
import io.harness.beans.provenance.ProvenancePredicate;
import io.harness.beans.provenance.RunDetails;
import io.harness.beans.steps.outcome.CIStepArtifactOutcome;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.slsa.beans.provenance.source.AcrSourceSpec;
import io.harness.slsa.beans.provenance.source.DockerSourceSpec;
import io.harness.slsa.beans.provenance.source.GcrSourceSpec;
import io.harness.slsa.beans.provenance.source.HarnessARSourceSpec;
import io.harness.slsa.beans.provenance.source.LocalSourceSpec;
import io.harness.slsa.beans.provenance.source.OthersSourceSpec;
import io.harness.slsa.beans.provenance.source.ProvenanceArtifactInfo;
import io.harness.slsa.beans.provenance.source.ProvenanceSource;
import io.harness.slsa.beans.provenance.source.ProvenanceSourceType;
import io.harness.slsa.execution.provenance.ProvenanceStepPluginUtils;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.beans.attestation.AttestationType;
import io.harness.ssca.beans.attestation.v1.AttestationV1;
import io.harness.ssca.beans.attestation.v1.CosignAttestationV1;
import io.harness.ssca.beans.stepinfo.ProvenanceStepInfo;
import io.harness.ssca.client.NgSettingsUtils;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.SSCA)
public class ProvenancePluginHelperTest extends CIExecutionTestBase {
  @InjectMocks private ProvenancePluginHelper provenancePluginHelper;
  @Mock private OutcomeService outcomeService;

  @Mock private SscaPluginUtils sscaPluginUtils;
  @Mock private NgSettingsUtils ngSettingsUtils;
  @Mock private CIFeatureFlagService featureFlagService;

  private Ambiance ambiance;

  private static final String CONNECTOR = "connectorRef";
  private static final String DOCKER_REPO = "library/nginx";
  private static final String TAG_1 = "latest";
  private static final String DIGEST_1 = "digest";
  private static final String COSIGN_PASS = "cosignPass";
  private static final String COSIGN_PRIVATE_KEY = "cosignKey";
  private static final String STEP_IDENTIFIER = "stepIdentifier_Provenance_Step";
  private static final String GCR_HOST = "us.gcr.io";
  private static final String HAR_URL = "pkg.harness.io";
  private static final String GCP_PROJECT = "my-project";
  private static final String ACR_REPO = "acrRegistry.azurecr.io/imageName";

  @Before
  public void setup() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");

    ambiance = Ambiance.newBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .setPipelineIdentifier("pipelineId")
                                    .setRunSequence(1)
                                    .setTriggerInfo(
                                        ExecutionTriggerInfo.newBuilder()
                                            .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("triggerBy").build())
                                            .setTriggerType(TriggerType.MANUAL)
                                            .build())
                                    .build())
                   .setPlanExecutionId("pipelineExecutionUuid")
                   .putAllSetupAbstractions(setupAbstractions)
                   .addLevels(Level.newBuilder()
                                  .setRuntimeId("runtimeId")
                                  .setIdentifier(STEP_IDENTIFIER)
                                  .setOriginalIdentifier("originalIdentifierId")
                                  .setRetryIndex(1)
                                  .build())
                   .build();

    when(sscaPluginUtils.getStageName(any())).thenReturn("stageName");
    when(sscaPluginUtils.getStageExecutionIdentifier(any())).thenReturn("stageExecutionId");
    when(sscaPluginUtils.getPipelineTriggerBy(any())).thenReturn("triggerBy");
    when(sscaPluginUtils.getPipelineTriggerType(any())).thenReturn("MANUAL");
    when(sscaPluginUtils.getPipelineTriggerByEmail(any())).thenReturn("triggerBy@harness.io");
    provenancePluginHelper.harnessArtifactRegistryUrl = HAR_URL;
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetDockerProvenanceStepEnvVariables() {
    when(ngSettingsUtils.getAirgapEnabled("accountId", "orgId", "projectId")).thenReturn(true);
    ProvenanceStepInfo provenanceStepInfo = getProvenanceForDockerSource();
    Map<String, String> envMap =
        provenancePluginHelper.getProvenanceStepEnvVariables(provenanceStepInfo, STEP_IDENTIFIER, ambiance, Type.K8);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(17);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REPO, DOCKER_REPO);
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REGISTRY_TYPE, "docker");
    expectedEnvMap.put(ProvenanceStepPluginUtils.ENABLE_SSCA_AIRGAP, "true");
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetDockerProvenanceStepEnvVariablesIfKeyIsNull() {
    ProvenanceStepInfo provenanceStepInfo = getProvenanceForDockerSource();
    CosignAttestationV1 cosignAttestationV1 = (CosignAttestationV1) provenanceStepInfo.getAttestation().getSpec();
    cosignAttestationV1.setKey(null);

    Map<String, String> envMap =
        provenancePluginHelper.getProvenanceStepEnvVariables(provenanceStepInfo, STEP_IDENTIFIER, ambiance, Type.K8);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(16);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REPO, DOCKER_REPO);
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REGISTRY_TYPE, "docker");
    expectedEnvMap.put(ProvenanceStepPluginUtils.VAULT_COSIGN_KEY_PATH, null);
    expectedEnvMap.values().removeAll(Collections.singleton(null));
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetGcrProvenanceStepEnvVariables() {
    ProvenanceStepInfo provenanceStepInfo = getProvenanceForGcrSource();
    Map<String, String> envMap =
        provenancePluginHelper.getProvenanceStepEnvVariables(provenanceStepInfo, STEP_IDENTIFIER, ambiance, Type.K8);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(18);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REPO, GCP_PROJECT + '/' + DOCKER_REPO);
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REGISTRY, GCR_HOST);
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REGISTRY_TYPE, "gcr");
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testGetAcrProvenanceStepEnvVariables() {
    ProvenanceStepInfo provenanceStepInfo = getProvenanceForAcrSource();
    Map<String, String> envMap =
        provenancePluginHelper.getProvenanceStepEnvVariables(provenanceStepInfo, STEP_IDENTIFIER, ambiance, Type.K8);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(18);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REPO, StringUtils.substringAfter(ACR_REPO, "/"));
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REGISTRY, StringUtils.substringBefore(ACR_REPO, "/"));
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REGISTRY_TYPE, "acr");
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetOthersProvenanceStepEnvVars() {
    ProvenanceStepInfo provenanceStepInfo = getProvenanceForOthersSource();
    OthersSourceSpec spec = (OthersSourceSpec) provenanceStepInfo.getSource().getSpec();
    Map<String, String> envMap =
        provenancePluginHelper.getProvenanceStepEnvVariables(provenanceStepInfo, STEP_IDENTIFIER, ambiance, Type.K8);
    List<ProvenanceArtifactInfo> provenanceArtifactInfo = resolveGenericListParameter(
        "artifacts", SscaConstants.SLSA_PROVENANCE, "identifier", spec.getArtifacts(), false);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(15);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_TAGS, null);
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_DIGESTS, null);
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REGISTRY_TYPE, "others");
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_ARTIFACT_INFO, JsonUtils.asJson(provenanceArtifactInfo));
    expectedEnvMap.values().removeAll(Collections.singleton(null));
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetHarProvenanceStepEnvVariables() {
    ProvenanceStepInfo provenanceStepInfo = getProvenanceForHarSource();
    doReturn(true).when(featureFlagService).isEnabled(FeatureName.HAR_ENABLED, AmbianceUtils.getAccountId(ambiance));
    Map<String, String> envMap =
        provenancePluginHelper.getProvenanceStepEnvVariables(provenanceStepInfo, STEP_IDENTIFIER, ambiance, Type.K8);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(16);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.remove(ProvenanceStepPluginUtils.PLUGIN_TAGS);
    expectedEnvMap.remove(ProvenanceStepPluginUtils.PLUGIN_REPO);
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REGISTRY, HAR_URL);
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REGISTRY_TYPE, "har");
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = AKSHAY_PANDEY)
  @Category(UnitTests.class)
  public void testGetLocalProvenanceStepEnvVariables() {
    ProvenanceStepInfo provenanceStepInfo = getProvenanceForLocalSource();
    Map<String, String> envMap =
        provenancePluginHelper.getProvenanceStepEnvVariables(provenanceStepInfo, STEP_IDENTIFIER, ambiance, Type.K8);
    assertThat(envMap).isNotNull().isNotEmpty();
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.remove(ProvenanceStepPluginUtils.PLUGIN_TAGS);
    expectedEnvMap.remove(ProvenanceStepPluginUtils.PLUGIN_DIGESTS);
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REGISTRY_TYPE, "local");
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_NON_CONTAINER_ARTIFACT_NAME, "my-service-1.0.0.jar");
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_WORKSPACE, "/harness/workspace/target");
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_LOCAL_FILE_VERSION, "1.0.0");
    expectedEnvMap.values().removeAll(Collections.singleton(null));
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetProvenanceSecretVars() {
    ProvenanceStepInfo provenanceStepInfo = getProvenanceForDockerSource();
    Map<String, SecretNGVariable> secretNGVariableMap =
        provenancePluginHelper.getProvenanceStepSecretVariables(provenanceStepInfo);
    assertThat(secretNGVariableMap).isNotNull().isNotEmpty().hasSize(4);
    assertThat(secretNGVariableMap.get(SSCA_SLSA_GENERATION_COSIGN_PASSWORD)).isNotNull();
    SecretNGVariable variable = secretNGVariableMap.get(SSCA_SLSA_GENERATION_COSIGN_PASSWORD);
    assertThat(variable.getType()).isEqualTo(NGVariableType.SECRET);
    assertThat(variable.getName()).isEqualTo(SSCA_SLSA_GENERATION_COSIGN_PASSWORD);
    assertThat(variable.getValue()).isNotNull();
    SecretRefData secretRefData = variable.getValue().getValue();
    assertThat(secretRefData).isNotNull();
    assertThat(secretRefData.getScope()).isEqualTo(Scope.PROJECT);
    assertThat(secretRefData.toSecretRefStringValue()).isEqualTo(COSIGN_PASS);

    ProvenanceStepInfo accountLevelSecretStepInfo =
        ProvenanceStepInfo.builder()
            .attestation(AttestationV1.builder()
                             .type(AttestationType.COSIGN)
                             .spec(CosignAttestationV1.builder().password("account.test").private_key("key").build())
                             .build())
            .build();
    Map<String, SecretNGVariable> secretVariableMap1 =
        provenancePluginHelper.getProvenanceStepSecretVariables(accountLevelSecretStepInfo);
    assertThat(secretVariableMap1).isNotEmpty().hasSize(4);
    assertThat(secretVariableMap1.get(SSCA_SLSA_GENERATION_COSIGN_PASSWORD)).isNotNull();
    assertThat(secretVariableMap1.get(SSCA_SLSA_GENERATION_COSIGN_PASSWORD).getValue().getValue()).isNotNull();
    assertThat(secretVariableMap1.get(SSCA_SLSA_GENERATION_COSIGN_PASSWORD).getValue().getValue().getScope())
        .isEqualTo(Scope.ACCOUNT);
    assertThat(secretVariableMap1.get(SSCA_SLSA_GENERATION_COSIGN_PASSWORD).getValue().getValue().getIdentifier())
        .isEqualTo("test");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetDockerProvenanceStepEnvVariablesAtRuntime() {
    ProvenanceStepInfo provenanceStepInfo = getProvenanceForDockerSource();
    provenanceStepInfo.setInternal(true);
    ProvenancePredicate predicate = getProvenancePredicate();
    mockOutcomeService(predicate);

    Map<String, String> envMap = provenancePluginHelper.getProvenanceStepEnvVariablesAtRuntime(
        provenanceStepInfo, STEP_IDENTIFIER, ambiance, Type.K8);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(18);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REPO, DOCKER_REPO);
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REGISTRY_TYPE, "docker");
    expectedEnvMap.put(ProvenanceStepPluginUtils.PROVENANCE_PREDICATE, JsonUtils.asJson(predicate));
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetGcrProvenanceStepEnvVariablesAtRuntime() {
    ProvenanceStepInfo provenanceStepInfo = getProvenanceForGcrSource();
    provenanceStepInfo.setInternal(true);
    ProvenancePredicate predicate = getProvenancePredicate();
    mockOutcomeService(predicate);

    Map<String, String> envMap = provenancePluginHelper.getProvenanceStepEnvVariablesAtRuntime(
        provenanceStepInfo, STEP_IDENTIFIER, ambiance, Type.K8);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(19);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REPO, GCP_PROJECT + '/' + DOCKER_REPO);
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REGISTRY, GCR_HOST);
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REGISTRY_TYPE, "gcr");
    expectedEnvMap.put(ProvenanceStepPluginUtils.PROVENANCE_PREDICATE, JsonUtils.asJson(predicate));
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testGetAcrProvenanceStepEnvVariablesAtRuntime() {
    ProvenanceStepInfo provenanceStepInfo = getProvenanceForAcrSource();
    provenanceStepInfo.setInternal(true);
    ProvenancePredicate predicate = getProvenancePredicate();
    mockOutcomeService(predicate);

    Map<String, String> envMap = provenancePluginHelper.getProvenanceStepEnvVariablesAtRuntime(
        provenanceStepInfo, STEP_IDENTIFIER, ambiance, Type.K8);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(19);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REPO, "imageName");
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REGISTRY, "acrRegistry.azurecr.io");
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REGISTRY_TYPE, "acr");
    expectedEnvMap.put(ProvenanceStepPluginUtils.PROVENANCE_PREDICATE, JsonUtils.asJson(predicate));
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetHarProvenanceStepEnvVariablesAtRuntime() {
    ProvenanceStepInfo provenanceStepInfo = getProvenanceForHarSource();
    doReturn(true).when(featureFlagService).isEnabled(FeatureName.HAR_ENABLED, AmbianceUtils.getAccountId(ambiance));
    provenanceStepInfo.setInternal(true);
    ProvenancePredicate predicate = getProvenancePredicate();
    mockOutcomeService(predicate);

    Map<String, String> envMap = provenancePluginHelper.getProvenanceStepEnvVariablesAtRuntime(
        provenanceStepInfo, STEP_IDENTIFIER, ambiance, Type.K8);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(18);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.remove(ProvenanceStepPluginUtils.PLUGIN_REPO);
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REGISTRY, HAR_URL);
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_REGISTRY_TYPE, "har");
    expectedEnvMap.put(ProvenanceStepPluginUtils.PROVENANCE_PREDICATE, JsonUtils.asJson(predicate));
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  private void mockOutcomeService(ProvenancePredicate predicate) {
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("artifact_stepIdentifier")))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(CIStepArtifactOutcome.builder()
                                     .stepArtifacts(
                                         StepArtifacts.builder()
                                             .provenanceArtifact(ProvenanceArtifact.builder()
                                                                     .predicate(predicate)
                                                                     .predicateType("slsaProvenance1")
                                                                     .build())
                                             .publishedImageArtifact(
                                                 PublishedImageArtifact.builder().digest(DIGEST_1).tag(TAG_1).build())
                                             .build())

                                     .build())
                        .build());
  }

  private ProvenanceStepInfo getProvenanceForDockerSource() {
    return ProvenanceStepInfo.builder()
        .source(ProvenanceSource.builder()
                    .type(ProvenanceSourceType.DOCKER)
                    .spec(DockerSourceSpec.builder()
                              .connector(ParameterField.createValueField(CONNECTOR))
                              .repo(ParameterField.createValueField(DOCKER_REPO))
                              .tags(ParameterField.createValueField(List.of(TAG_1)))
                              .digest(ParameterField.createValueField(DIGEST_1))
                              .build())
                    .build())
        .attestation(AttestationV1.builder()
                         .type(AttestationType.COSIGN)
                         .spec(CosignAttestationV1.builder()
                                   .key(ParameterField.createValueField("key"))
                                   .password(COSIGN_PASS)
                                   .private_key(COSIGN_PRIVATE_KEY)
                                   .build())
                         .build())
        .build();
  }

  private ProvenanceStepInfo getProvenanceForGcrSource() {
    return ProvenanceStepInfo.builder()
        .source(ProvenanceSource.builder()
                    .type(ProvenanceSourceType.GCR)
                    .spec(GcrSourceSpec.builder()
                              .connector(ParameterField.createValueField(CONNECTOR))
                              .imageName(ParameterField.createValueField(DOCKER_REPO))
                              .host(ParameterField.createValueField(GCR_HOST))
                              .projectID(ParameterField.createValueField(GCP_PROJECT))
                              .tags(ParameterField.createValueField(List.of(TAG_1)))
                              .digest(ParameterField.createValueField(DIGEST_1))
                              .build())
                    .build())
        .attestation(AttestationV1.builder()
                         .type(AttestationType.COSIGN)
                         .spec(CosignAttestationV1.builder()
                                   .key(ParameterField.createValueField("key"))
                                   .password(COSIGN_PASS)
                                   .private_key(COSIGN_PRIVATE_KEY)
                                   .build())
                         .build())
        .build();
  }

  private ProvenanceStepInfo getProvenanceForAcrSource() {
    return ProvenanceStepInfo.builder()
        .source(ProvenanceSource.builder()
                    .type(ProvenanceSourceType.ACR)
                    .spec(AcrSourceSpec.builder()
                              .connector(ParameterField.createValueField(CONNECTOR))
                              .repository(ParameterField.createValueField(ACR_REPO))
                              .tags(ParameterField.createValueField(List.of(TAG_1)))
                              .digest(ParameterField.createValueField(DIGEST_1))
                              .build())
                    .build())
        .attestation(AttestationV1.builder()
                         .type(AttestationType.COSIGN)
                         .spec(CosignAttestationV1.builder()
                                   .password(COSIGN_PASS)
                                   .key(ParameterField.createValueField("key"))
                                   .private_key(COSIGN_PRIVATE_KEY)
                                   .build())
                         .build())
        .build();
  }

  private ProvenanceStepInfo getProvenanceForHarSource() {
    return ProvenanceStepInfo.builder()
        .source(ProvenanceSource.builder()
                    .type(ProvenanceSourceType.HARNESS_AR)
                    .spec(HarnessARSourceSpec.builder()
                              .registry(ParameterField.createValueField("registry"))
                              .image(ParameterField.createValueField("image"))
                              .digest(ParameterField.createValueField(DIGEST_1))
                              .build())
                    .build())
        .attestation(AttestationV1.builder()
                         .type(AttestationType.COSIGN)
                         .spec(CosignAttestationV1.builder()
                                   .password(COSIGN_PASS)
                                   .key(ParameterField.createValueField("key"))
                                   .private_key(COSIGN_PRIVATE_KEY)
                                   .build())
                         .build())
        .build();
  }

  private ProvenancePredicate getProvenancePredicate() {
    return ProvenancePredicate.builder()
        .buildDefinition(BuildDefinition.builder()
                             .buildType("https://developer.harness.io/docs/continuous-integration")
                             .internalParameters(InternalParameters.builder()
                                                     .accountId("accountId")
                                                     .pipelineExecutionId("pipelineExecutionId")
                                                     .pipelineIdentifier("pipelineId")
                                                     .build())
                             .build())
        .runDetails(
            RunDetails.builder()
                .builder(
                    ProvenanceBuilder.builder().id("https://developer.harness.io/docs/continuous-integration").build())
                .metadata(Metadata.builder().invocationId("runtimeId").startedOn("0").finishedOn("1").build())
                .build())
        .build();
  }

  private ProvenanceStepInfo getProvenanceForLocalSource() {
    return ProvenanceStepInfo.builder()
        .source(ProvenanceSource.builder()
                    .type(ProvenanceSourceType.LOCAL)
                    .spec(LocalSourceSpec.builder()
                              .workspace(ParameterField.createValueField("/harness/workspace/target"))
                              .artifactName(ParameterField.createValueField("my-service-1.0.0.jar"))
                              .version(ParameterField.createValueField("1.0.0"))
                              .build())
                    .build())
        .attestation(AttestationV1.builder()
                         .type(AttestationType.COSIGN)
                         .spec(CosignAttestationV1.builder()
                                   .key(ParameterField.createValueField("key"))
                                   .password(COSIGN_PASS)
                                   .private_key(COSIGN_PRIVATE_KEY)
                                   .build())
                         .build())
        .build();
  }

  private ProvenanceStepInfo getProvenanceForOthersSource() {
    return ProvenanceStepInfo.builder()
        .source(ProvenanceSource.builder()
                    .type(ProvenanceSourceType.OTHERS)
                    .spec(OthersSourceSpec.builder().artifacts(getProvenanceArtifactInfos()).build())
                    .build())
        .attestation(AttestationV1.builder()
                         .type(AttestationType.COSIGN)
                         .spec(CosignAttestationV1.builder()
                                   .key(ParameterField.createValueField("key"))
                                   .password(COSIGN_PASS)
                                   .private_key(COSIGN_PRIVATE_KEY)
                                   .build())
                         .build())
        .build();
  }

  private ParameterField<List<ProvenanceArtifactInfo>> getProvenanceArtifactInfos() {
    ProvenanceArtifactInfo provenanceArtifactInfo1 = new ProvenanceArtifactInfo();
    provenanceArtifactInfo1.setName("artifactName1");
    provenanceArtifactInfo1.setDigest(ParameterField.createValueField("artifactDigest1"));

    ProvenanceArtifactInfo provenanceArtifactInfo2 = new ProvenanceArtifactInfo();
    provenanceArtifactInfo2.setName("artifactName2");
    provenanceArtifactInfo2.setDigest(ParameterField.createValueField("artifactDigest2"));

    ParameterField<List<ProvenanceArtifactInfo>> artifacts = new ParameterField<>();
    artifacts.setValue(List.of(provenanceArtifactInfo1, provenanceArtifactInfo2));
    return artifacts;
  }

  private Map<String, String> getExpectedEnvMap() {
    Map<String, String> expectedEnvMap = new HashMap<>();
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_TAGS, TAG_1);
    expectedEnvMap.put(ProvenanceStepPluginUtils.STEP_EXECUTION_ID, "runtimeId");
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_DIGESTS, DIGEST_1);
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_TYPE, "attest");
    expectedEnvMap.put(ProvenanceStepPluginUtils.STAGE_EXECUTION_ID, "stageExecutionId");
    expectedEnvMap.put(ProvenanceStepPluginUtils.STAGE_NAME, "stageName");
    expectedEnvMap.put(ProvenanceStepPluginUtils.STEP_NAME, "ProvenanceStep");
    expectedEnvMap.put(ProvenanceStepPluginUtils.STEP_ID, "stepIdentifier_Provenance_Step");
    expectedEnvMap.put(ProvenanceStepPluginUtils.VAULT_COSIGN_KEY_PATH, "key");
    expectedEnvMap.put(ProvenanceStepPluginUtils.PLUGIN_BASE64_SECRET, "false");
    expectedEnvMap.put(ProvenanceStepPluginUtils.ENABLE_SSCA_AIRGAP, "false");
    expectedEnvMap.put(ProvenanceStepPluginUtils.PIPELINE_TRIGGER_TYPE, "MANUAL");
    expectedEnvMap.put(ProvenanceStepPluginUtils.PIPELINE_TRIGGER_BY, "triggerBy");
    expectedEnvMap.put(ProvenanceStepPluginUtils.PIPELINE_TRIGGER_BY_EMAIL, "triggerBy@harness.io");
    expectedEnvMap.put(ProvenanceStepPluginUtils.BUILD_INFRA_TYPE, "K8");
    return expectedEnvMap;
  }
}
