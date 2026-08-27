/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.MOHD_FAIZ;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.SourceType;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.plan.creation.info.PlanCreatorServiceInfo;
import io.harness.pms.plan.creation.validator.PlanCreationValidator;
import io.harness.pms.plan.utils.PlanExecutionContextMapper;
import io.harness.pms.sdk.helper.PmsSdkHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.kryo.NGCommonsKryoRegistrar;
import io.harness.serializer.kryo.YamlKryoRegistrar;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(PIPELINE)
public class PlanCreatorMergeServiceTest extends CategoryTest {
  private KryoSerializer kryoSerializer;
  @Mock private PmsFeatureFlagService pmsFeatureFlagServiceMock;
  @Mock private PmsEventSender pmsEventSender;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private PmsSdkHelper pmsSdkHelper;
  @Mock private PlanCreationValidator planCreationValidator;
  @Spy @InjectMocks PlanCreatorMergeService planCreatorMergeServiceMock;
  @Mock NGSettingsClient ngSettingsClient;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final String accountId = "acc";
  private final String orgId = "org";
  private final String projId = "proj";
  private final String projectUniqueId = "projectUniqueId";
  private ExecutionMetadata executionMetadata;
  private PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
      PlanExecutionMetadataWithContext.builder().build();
  private String pipelineYamlV1;

  private String processedYaml = "{\"pipeline\":{\"identifier\":\"p1_inline\",\"name\":\"p1_inline\","
      + "\"projectIdentifier\":\"proj1\",\"orgIdentifier\":"
      + "\"default\",\"tags\":{\"__uuid\":\"9Sd2vBJyTreQuN50aBigOw\"},\"stages\":[{\"stage\":{\"identifier\":\"ap1\","
      + "\"type\":"
      + "\"Approval\",\"name\":\"ap1\",\"description\":\"\",\"spec\":{\"execution\":{\"steps\":[{\"step\":{"
      + "\"identifier\":\"ap1\",\"type\":"
      + "\"HarnessApproval\",\"name\":\"ap1\",\"timeout\":\"1d\",\"spec\":{\"approvalMessage\":"
      + "\"Please review the following information\\nand approve the pipeline "
      + "progression\",\"includePipelineExecutionHistory\":"
      + "true,\"approvers\":{\"minimumCount\":1,\"disallowPipelineExecutor\":false,\"userGroups\":"
      + "[\"account._account_all_users\"],\"__uuid\":\"huEePX9KTxO2bxiJScw3iQ\"},\"isAutoRejectEnabled\":false,"
      + "\"approverInputs\":[],\"__uuid\":"
      + "\"Q8HzGsYWSZGjvjmf5Jlszw\"},\"__uuid\":\"tCucaMc3THa3qh6MXcMVkw\"},\"__uuid\":\"IN87E8WJT4-YCnhHkIpV2A\"}],\"_"
      + "_uuid\":"
      + "\"sEDjjPPhQNe8wJgvpz6GsA\"},\"__uuid\":\"SlBHSpltS2ePxjN4LUnEGw\"},\"tags\":{\"__uuid\":"
      + "\"j6V5id3tQ6S9fkED430CXw\"},\"__uuid\":"
      + "\"JwAB6BpnTF-PGO6FgrLXlw\"},\"__uuid\":\"GMi_oA0MQYm6oGAJXR9Dig\"}],\"__uuid\":\"R1f_rPxfSUCrc5_MvfFHSw\"},\"_"
      + "_uuid\":\"fSqM9u1jQsK8cJngoac2FA\"}";

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
    kryoSerializer =
        new KryoSerializer(new HashSet<>(Arrays.asList(NGCommonsKryoRegistrar.class, YamlKryoRegistrar.class)));
    executionMetadata = ExecutionMetadata.newBuilder()
                            .setExecutionUuid("execId")
                            .setRunSequence(3)
                            .setModuleType("cd")
                            .setPipelineIdentifier("pipelineId")
                            .setHarnessVersion("0")
                            .build();
    pipelineYamlV1 = readFile("pipeline-v1.yaml");
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  private ScopeInfo getScopeInfo() {
    return ScopeInfo.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projId)
        .uniqueId(projectUniqueId)
        .build();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreateInitialPlanCreationContext() {
    PlanCreatorMergeService planCreatorMergeService = new PlanCreatorMergeService(null, null, null, null,
        Executors.newSingleThreadExecutor(), 20, null, ngSettingsClient, pmsFeatureFlagHelper, null);
    Mockito.when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_DISABLE_MAX_TIMEOUT_CONFIG))
        .thenReturn(true);
    Map<String, PlanCreationContextValue> initialPlanCreationContext =
        planCreatorMergeService.createInitialPlanCreationContext(
            accountId, orgId, projId, executionMetadata, planExecutionMetadataWithContext, getScopeInfo(), true);
    Map<String, String> settingsValueMap = new HashMap<>();
    settingsValueMap.put("pipeline_timeout", "8w");
    settingsValueMap.put("stage_timeout", "8w");
    assertThat(initialPlanCreationContext).hasSize(1);
    assertThat(initialPlanCreationContext.containsKey("metadata")).isTrue();
    PlanCreationContextValue planCreationContextValue = initialPlanCreationContext.get("metadata");
    assertThat(planCreationContextValue.getAccountIdentifier()).isEqualTo(accountId);
    assertTrue(planCreationContextValue.getIsExecutionInputEnabled());
    assertThat(planCreationContextValue.getOrgIdentifier()).isEqualTo(orgId);
    assertThat(planCreationContextValue.getProjectIdentifier()).isEqualTo(projId);
    assertThat(planCreationContextValue.getParentUniqueId()).isEqualTo(projectUniqueId);
    assertThat(planCreationContextValue.getExecutionContext())
        .isEqualTo(
            PlanExecutionContextMapper.toExecutionContext(executionMetadata, settingsValueMap, Collections.emptyMap()));
    assertThat(planCreationContextValue.getTriggerPayload()).isEqualTo(TriggerPayload.newBuilder().build());

    TriggerPayload triggerPayload = TriggerPayload.newBuilder()
                                        .setParsedPayload(ParsedPayload.newBuilder().build())
                                        .setSourceType(SourceType.GITHUB_REPO)
                                        .build();
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().triggerPayload(triggerPayload).build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .triggerPayload(triggerPayload)
            .planExecutionMetadata(planExecutionMetadata)
            .build();
    initialPlanCreationContext = planCreatorMergeService.createInitialPlanCreationContext(
        accountId, orgId, projId, executionMetadata, planExecutionMetadataWithContext, null, false);
    assertThat(initialPlanCreationContext).hasSize(1);
    assertThat(initialPlanCreationContext.containsKey("metadata")).isTrue();
    planCreationContextValue = initialPlanCreationContext.get("metadata");
    assertThat(planCreationContextValue.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(planCreationContextValue.getOrgIdentifier()).isEqualTo(orgId);
    assertThat(planCreationContextValue.getProjectIdentifier()).isEqualTo(projId);
    assertThat(planCreationContextValue.getExecutionContext())
        .isEqualTo(
            PlanExecutionContextMapper.toExecutionContext(executionMetadata, settingsValueMap, Collections.emptyMap()));
    assertThat(planCreationContextValue.getTriggerPayload()).isEqualTo(triggerPayload);
    assertThat(planCreationContextValue.getIsExecutionInputEnabled()).isTrue();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testCreateInitialPlanCreationContextForV1Yaml() {
    ExecutionMetadata executionMetadataLocal =
        executionMetadata.toBuilder().setHarnessVersion(HarnessYamlVersion.V1).build();
    String pipelineYaml = readFile("pipeline-v1-new.yaml");
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .processedYaml(pipelineYaml)
            .planExecutionMetadata(planExecutionMetadata)
            .build();
    PlanCreatorMergeService planCreatorMergeService = new PlanCreatorMergeService(null, null, null, null,
        Executors.newSingleThreadExecutor(), 20, kryoSerializer, ngSettingsClient, pmsFeatureFlagHelper, null);
    Map<String, PlanCreationContextValue> initialPlanCreationContext =
        planCreatorMergeService.createInitialPlanCreationContext(
            accountId, orgId, projId, executionMetadataLocal, planExecutionMetadataWithContext, null, false);
    assertThat(initialPlanCreationContext).containsKey("metadata");
    PlanCreationContextValue planCreationContextValue = initialPlanCreationContext.get("metadata");
    Map<String, String> settingsValueMap = new HashMap<>();
    settingsValueMap.put("pipeline_timeout", "8w");
    settingsValueMap.put("stage_timeout", "8w");
    assertThat(planCreationContextValue.getIsExecutionInputEnabled()).isTrue();
    assertThat(planCreationContextValue.getExecutionContext())
        .isEqualTo(PlanExecutionContextMapper.toExecutionContext(executionMetadataLocal, settingsValueMap,
            Map.of(PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION.name(), true)));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testCreateInitialPlanCreationContextForV1YamlWithStaticReference() {
    ExecutionMetadata executionMetadataLocal =
        executionMetadata.toBuilder().setHarnessVersion(HarnessYamlVersion.V1).build();
    String pipelineYaml = readFile("pipeline-v1-new.yaml");
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .processedYaml(pipelineYaml)
            .planExecutionMetadata(planExecutionMetadata)
            .build();
    PlanCreatorMergeService planCreatorMergeService = new PlanCreatorMergeService(null, null, null, null,
        Executors.newSingleThreadExecutor(), 20, kryoSerializer, ngSettingsClient, pmsFeatureFlagHelper, null);
    Map<String, PlanCreationContextValue> initialPlanCreationContext =
        planCreatorMergeService.createInitialPlanCreationContext(
            accountId, orgId, projId, executionMetadataLocal, planExecutionMetadataWithContext, null, false);
    assertThat(initialPlanCreationContext).containsKey("metadata");
    PlanCreationContextValue planCreationContextValue = initialPlanCreationContext.get("metadata");
    Map<String, String> settingsValueMap = new HashMap<>();
    settingsValueMap.put("pipeline_timeout", "8w");
    settingsValueMap.put("stage_timeout", "8w");
    assertThat(planCreationContextValue.getIsExecutionInputEnabled()).isTrue();
    assertThat(planCreationContextValue.getExecutionContext())
        .isEqualTo(PlanExecutionContextMapper.toExecutionContext(executionMetadataLocal, settingsValueMap,
            Map.of(PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION.name(), true)));
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testCreatePlanVersioned() throws IOException {
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().build();
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().triggerPayload(triggerPayload).build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .processedYaml(processedYaml)
            .triggerPayload(triggerPayload)
            .planExecutionMetadata(planExecutionMetadata)
            .build();
    Set<String> supportedTypesForPipeline = new HashSet<>();
    supportedTypesForPipeline.add("[__any__]");
    Map<String, Set<String>> supportedTypes = new HashMap<>();
    supportedTypes.put("pipeline", supportedTypesForPipeline);
    PlanCreatorServiceInfo planCreatorServiceInfo = new PlanCreatorServiceInfo(supportedTypes, null, 1);

    Map<String, PlanCreatorServiceInfo> services = new HashMap<>();
    services.put("pms", planCreatorServiceInfo);

    doReturn(services).when(pmsSdkHelper).getServicesV2();
    doReturn(true).when(pmsFeatureFlagServiceMock).isEnabled(any(), (FeatureName) any());
    PlanCreationBlobResponse finalResponse = PlanCreationBlobResponse.newBuilder().build();
    doReturn(finalResponse)
        .when(planCreatorMergeServiceMock)
        .createPlanForDependenciesRecursive(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    doNothing().when(planCreationValidator).validate(any(), any());
    planCreatorMergeServiceMock.createPipelinePlanVersion(accountId, orgId, projId, HarnessYamlVersion.V1,
        executionMetadata, planExecutionMetadataWithContext, null, false);
    verify(planCreationValidator, times(1)).validate(any(), any());
    verify(planCreatorMergeServiceMock, times(1))
        .createPlanForDependenciesRecursive(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testCreatePlanVersionedWithScopeInfo() throws IOException {
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().build();
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().triggerPayload(triggerPayload).build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .processedYaml(processedYaml)
            .triggerPayload(triggerPayload)
            .planExecutionMetadata(planExecutionMetadata)
            .build();
    Set<String> supportedTypesForPipeline = new HashSet<>();
    supportedTypesForPipeline.add("[__any__]");
    Map<String, Set<String>> supportedTypes = new HashMap<>();
    supportedTypes.put("pipeline", supportedTypesForPipeline);
    PlanCreatorServiceInfo planCreatorServiceInfo = new PlanCreatorServiceInfo(supportedTypes, null, 1);

    Map<String, PlanCreatorServiceInfo> services = new HashMap<>();
    services.put("pms", planCreatorServiceInfo);

    doReturn(services).when(pmsSdkHelper).getServicesV2();
    doReturn(true).when(pmsFeatureFlagServiceMock).isEnabled(any(), (FeatureName) any());
    PlanCreationBlobResponse finalResponse = PlanCreationBlobResponse.newBuilder().build();
    doReturn(finalResponse)
        .when(planCreatorMergeServiceMock)
        .createPlanForDependenciesRecursive(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    doNothing().when(planCreationValidator).validate(any(), any());
    planCreatorMergeServiceMock.createPipelinePlanVersion(accountId, orgId, projId, HarnessYamlVersion.V1,
        executionMetadata, planExecutionMetadataWithContext, getScopeInfo(), true);
    verify(planCreationValidator, times(1)).validate(any(), any());
    verify(planCreatorMergeServiceMock, times(1))
        .createPlanForDependenciesRecursive(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testCreatePlanVersionedWithUnresolvableDependencies() {
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().build();
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().triggerPayload(triggerPayload).build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .processedYaml(processedYaml)
            .triggerPayload(triggerPayload)
            .planExecutionMetadata(planExecutionMetadata)
            .build();
    Map<String, PlanCreatorServiceInfo> services = new LinkedHashMap<>();
    services.put("cd", new PlanCreatorServiceInfo(new HashMap<>(), null, 0));
    services.put("pms", new PlanCreatorServiceInfo(new HashMap<>(), null, 1));
    services.put("ci", new PlanCreatorServiceInfo(new HashMap<>(), null, 2));
    services.put("sto", new PlanCreatorServiceInfo(new HashMap<>(), null, 3));

    Set<String> supportedTypesForPipeline = new HashSet<>();
    supportedTypesForPipeline.add("[__any__]");
    Map<String, Set<String>> supportedTypes = new HashMap<>();
    supportedTypes.put("pipeline", supportedTypesForPipeline);

    doReturn(services).when(pmsSdkHelper).getServicesV2();
    doReturn(false)
        .when(pmsFeatureFlagHelper)
        .isEnabled(accountId, FeatureName.CDS_CREATE_MERGE_PLAN_V2_OPTIMIZED_FLOW);
    assertThatThrownBy(()
                           -> planCreatorMergeServiceMock.createPipelinePlanVersion(accountId, orgId, projId,
                               HarnessYamlVersion.V1, executionMetadata, planExecutionMetadataWithContext, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Following yaml paths could not be parsed: ");
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testGetFeatureFlagMapWhenGitOpsSendStatusDisableGateOn() {
    Mockito.when(pmsFeatureFlagHelper.isEnabled(Mockito.eq(accountId), Mockito.anyString())).thenReturn(false);
    Mockito
        .when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED.toString()))
        .thenReturn(true);

    Map<String, Boolean> featureFlagMap =
        planCreatorMergeServiceMock.getFeatureFlagMap(accountId, HarnessYamlVersion.V0);

    assertThat(featureFlagMap).containsEntry(FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED.toString(), true);
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testGetFeatureFlagMapWhenGitOpsSendStatusDisableGateOff() {
    Mockito.when(pmsFeatureFlagHelper.isEnabled(Mockito.eq(accountId), Mockito.anyString())).thenReturn(false);

    Map<String, Boolean> featureFlagMap =
        planCreatorMergeServiceMock.getFeatureFlagMap(accountId, HarnessYamlVersion.V0);

    assertThat(featureFlagMap).doesNotContainKey(FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED.toString());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCreateInitialPlanCreationContext2() {
    PlanCreatorMergeService planCreatorMergeService = new PlanCreatorMergeService(null, null, null, null,
        Executors.newSingleThreadExecutor(), 20, null, ngSettingsClient, pmsFeatureFlagHelper, null);
    Mockito.when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_DISABLE_MAX_TIMEOUT_CONFIG))
        .thenReturn(true);
    Map<String, PlanCreationContextValue> initialPlanCreationContext =
        planCreatorMergeService.createInitialPlanCreationContext(
            accountId, orgId, projId, executionMetadata, planExecutionMetadataWithContext, null, false);
    assertThat(initialPlanCreationContext).isNotEmpty();
    PlanCreationContextValue planCreationContextValue = initialPlanCreationContext.get("metadata");
    assertThat(planCreationContextValue.getExecutionContext().getSettingToValueMapMap().get("pipeline_timeout"))
        .isEqualTo("8w");
    assertThat(planCreationContextValue.getExecutionContext().getSettingToValueMapMap().get("stage_timeout"))
        .isEqualTo("8w");
  }
}
