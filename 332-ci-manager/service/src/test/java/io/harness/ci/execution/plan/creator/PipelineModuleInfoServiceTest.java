/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator;

import static io.harness.eventsframework.schemas.platform.ArchitectureType.ARCHITECTURE_TYPE_ARM64;
import static io.harness.eventsframework.schemas.platform.BuildInfraType.BUILD_INFRA_TYPE_CLOUD;
import static io.harness.eventsframework.schemas.platform.ModuleName.MODULE_NAME_CI;
import static io.harness.eventsframework.schemas.platform.OSType.OSTYPE_LINUX;
import static io.harness.eventsframework.schemas.platform.ResourceClass.RESOURCE_CLASS_XLARGE;
import static io.harness.rule.OwnerRule.DHIRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ci.beans.entities.PipelineModuleInfoEntity;
import io.harness.ci.beans.entities.StageModuleInfoEntity;
import io.harness.ci.execution.states.IntegrationStageStepPMS;
import io.harness.ci.plan.creator.execution.CIPipelineStageModuleInfo;
import io.harness.eventsframework.schemas.platform.Developer;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.repositories.PipelineModuleInfoRepository;
import io.harness.rule.Owner;
import io.harness.utils.CILicenseUsageUtils;
import io.harness.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CI)
public class PipelineModuleInfoServiceTest {
  @Mock private PipelineModuleInfoRepository repository;
  @Mock private CILicenseUsageUtils ciLicenseUsageUtils;

  @InjectMocks private PipelineModuleInfoService service;

  @Captor private ArgumentCaptor<StageModuleInfoEntity> stageEntityCaptor;

  private static final String ACCOUNT_ID = "test-account";
  private static final String ORG_ID = "test-org";
  private static final String PROJECT_ID = "test-project";
  private static final String PIPELINE_ID = "test-pipeline";
  private static final String PLAN_EXECUTION_ID = "test-plan-execution";
  private static final String STAGE_EXECUTION_ID = "test-stage-execution";
  private static final String STAGE_ID = "test-stage-id";
  private static final String STAGE_NAME = "Test Build Stage";
  private static final String PARENT_UNIQUE_ID = "test-parent-unique-id";

  private OrchestrationEvent event;
  private Ambiance ambiance;
  private CIPipelineStageModuleInfo stageModuleInfo;
  private PipelineModuleInfoEntity updatedEntity;
  private StepType ciStepType;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    // Create step type for CI
    ciStepType = StepType.newBuilder().setType("CI").build();

    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", ACCOUNT_ID);
    setupAbstractions.put("orgIdentifier", ORG_ID);
    setupAbstractions.put("projectIdentifier", PROJECT_ID);
    setupAbstractions.put("parentUniqueId", PARENT_UNIQUE_ID);

    // Setup level with proper metadata
    Level stageLevel = Level.newBuilder()
                           .setRuntimeId(STAGE_EXECUTION_ID)
                           .setSetupId(STAGE_ID)
                           .setIdentifier("stage_1")
                           .setStepType(IntegrationStageStepPMS.STEP_TYPE)
                           .build();

    // Setup ambiance properly with setupAbstractions (standard pattern in Harness)
    ambiance = Ambiance.newBuilder()
                   .setPlanExecutionId(PLAN_EXECUTION_ID)
                   .setStageExecutionId(STAGE_EXECUTION_ID)
                   .addLevels(stageLevel)
                   .putAllSetupAbstractions(setupAbstractions)
                   .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_ID).build())
                   .build();

    // Create orchestration event per Harness standard
    event = OrchestrationEvent.builder().ambiance(ambiance).status(Status.RUNNING).serviceName("ci").build();

    // Set up domain model with realistic values
    stageModuleInfo = createStageDomainModel();

    // Set up entity model that would be returned after persistence
    updatedEntity = createEntityWithStage();

    // Set up mock responses for utility methods using correct proto enums
    when(ciLicenseUsageUtils.getModuleName(any(StepType.class))).thenReturn(MODULE_NAME_CI);
    when(ciLicenseUsageUtils.getArchitectureType("arm64")).thenReturn(ARCHITECTURE_TYPE_ARM64);
    when(ciLicenseUsageUtils.getBuildInfraType("Self-Hosted")).thenReturn(BUILD_INFRA_TYPE_CLOUD);
    when(ciLicenseUsageUtils.getOSType("Linux")).thenReturn(OSTYPE_LINUX);
    when(ciLicenseUsageUtils.getResourceClass("xlarge")).thenReturn(RESOURCE_CLASS_XLARGE);
    when(ciLicenseUsageUtils.getDevelopers(any(Ambiance.class))).thenReturn(createDevelopers());
    // Mock getBuilderMultiplier to return the expected value for xlarge Linux
    when(ciLicenseUsageUtils.getBuilderMultiplier(ACCOUNT_ID, "xlarge", "Linux", "arm64")).thenReturn(20.0);

    // Mock repository behavior
    when(repository.addStageModuleInfo(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
             any(StageModuleInfoEntity.class)))
        .thenReturn(updatedEntity);
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testSaveStageModuleInfo_SuccessfulConversionAndPersistence() {
    // When
    service.saveStageModuleInfo(event, stageModuleInfo, ciStepType);

    // Then
    verify(repository)
        .addStageModuleInfo(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID), eq(PLAN_EXECUTION_ID),
            eq(PARENT_UNIQUE_ID), stageEntityCaptor.capture());

    // Verify the conversion from domain model to entity model is accurate
    StageModuleInfoEntity capturedEntity = stageEntityCaptor.getValue();
    assertThat(capturedEntity.getStageExecutionId()).isEqualTo(stageModuleInfo.getStageExecutionId());
    assertThat(capturedEntity.getStageId()).isEqualTo(stageModuleInfo.getStageId());
    assertThat(capturedEntity.getStageName()).isEqualTo(stageModuleInfo.getStageName());
    assertThat(capturedEntity.getCpuTime()).isEqualTo(stageModuleInfo.getCpuTime());
    assertThat(capturedEntity.getStageBuildTime()).isEqualTo(stageModuleInfo.getStageBuildTime());
    assertThat(capturedEntity.getBuildMultiplier()).isEqualTo(stageModuleInfo.getBuildMultiplier());
    assertThat(capturedEntity.getOptimizationState()).isEqualTo(stageModuleInfo.getOptimizationState());
    assertThat(capturedEntity.getTimeSaved()).isEqualTo(stageModuleInfo.getTimeSaved());
    assertThat(capturedEntity.getQueueTimeMs()).isEqualTo(stageModuleInfo.getQueueTimeMs());
    assertThat(capturedEntity.getCommitMessage()).isEqualTo(stageModuleInfo.getCommitMessage());
    assertThat(capturedEntity.getPrTitle()).isEqualTo(stageModuleInfo.getPrTitle());

    // Verify that the utility methods were called to convert specific fields
    verify(ciLicenseUsageUtils).getModuleName(ciStepType);
    verify(ciLicenseUsageUtils).getArchitectureType(stageModuleInfo.getOsArch());
    verify(ciLicenseUsageUtils).getBuildInfraType(stageModuleInfo.getInfraType());
    verify(ciLicenseUsageUtils).getOSType(stageModuleInfo.getOsType());
    verify(ciLicenseUsageUtils).getResourceClass(stageModuleInfo.getResourceClass());
    verify(ciLicenseUsageUtils).getDevelopers(ambiance);
    // Verify that getBuilderMultiplier was called with the correct parameters
    verify(ciLicenseUsageUtils)
        .getBuilderMultiplier(
            ACCOUNT_ID, stageModuleInfo.getResourceClass(), stageModuleInfo.getOsType(), stageModuleInfo.getOsArch());

    // Verify build minutes are calculated correctly
    assertThat(capturedEntity.getBuildMinutes())
        .isEqualTo(DateTimeUtils.roundToNearestMinute(stageModuleInfo.getCpuTime()));
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testSaveStageModuleInfo_HandlesExceptions() {
    // Simulate an exception during repository operation
    doThrow(new RuntimeException("Simulated database error"))
        .when(repository)
        .addStageModuleInfo(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
            any(StageModuleInfoEntity.class));

    // When - the service should catch the exception and not propagate it
    service.saveStageModuleInfo(event, stageModuleInfo, ciStepType);

    // Then - verify the method completed without throwing an exception
    // and the repository was called
    verify(repository)
        .addStageModuleInfo(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID), eq(PLAN_EXECUTION_ID),
            eq(PARENT_UNIQUE_ID), any(StageModuleInfoEntity.class));
  }

  private CIPipelineStageModuleInfo createStageDomainModel() {
    return CIPipelineStageModuleInfo.builder()
        .stageExecutionId(STAGE_EXECUTION_ID)
        .stageId(STAGE_ID)
        .stageName(STAGE_NAME)
        .status("SUCCESS")
        .cpuTime(1000L)
        .stageBuildTime(2000L)
        .infraType("Self-Hosted")
        .osType("Linux")
        .osArch("arm64")
        .startTs(1620000000L)
        .buildMultiplier(20.0)
        .resourceClass("xlarge")
        .optimizationState("OPTIMIZED")
        .timeSaved(500L)
        .commitId("abc123")
        .repoName("https://github.com/org/repo")
        .queueTimeMs(1500L)
        .commitMessage("fix: resolve null pointer exception")
        .prTitle("Fix NPE in pipeline executor")
        .build();
  }

  private PipelineModuleInfoEntity createEntityWithStage() {
    return PipelineModuleInfoEntity.builder()
        .uuid("test-uuid")
        .accountIdentifier(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projIdentifier(PROJECT_ID)
        .pipelineIdentifier(PIPELINE_ID)
        .planExecutionId(PLAN_EXECUTION_ID)
        .stageModuleInfoList(Collections.singletonList(createStageEntityModel()))
        .createdAt(1620000000L)
        .updatedAt(1620001000L)
        .build();
  }

  private StageModuleInfoEntity createStageEntityModel() {
    return StageModuleInfoEntity.builder()
        .stageExecutionId(STAGE_EXECUTION_ID)
        .stageId(STAGE_ID)
        .stageName(STAGE_NAME)
        .status("SUCCESS")
        .cpuTime(1000L)
        .stageBuildTime(2000L)
        .buildMultiplier(20.0)
        .optimizationState("OPTIMIZED")
        .timeSaved(500L)
        .architectureType(ARCHITECTURE_TYPE_ARM64)
        .buildInfraType(BUILD_INFRA_TYPE_CLOUD)
        .infraOSType(OSTYPE_LINUX)
        .infraResourceClass(RESOURCE_CLASS_XLARGE)
        .startTimestamp(1620000000L)
        .commitId("abc123")
        .repoName("https://github.com/org/repo")
        .buildMinutes(1)
        .lastBuildTimestamp(1620000000L)
        .committers(createDevelopers())
        .moduleName(MODULE_NAME_CI)
        .build();
  }

  private List<Developer> createDevelopers() {
    List<Developer> developers = new ArrayList<>();
    // Proper way to create Developer objects in Harness
    developers.add(Developer.newBuilder().setEmail("user1@harness.io").setName("User One").build());
    developers.add(Developer.newBuilder().setEmail("user2@harness.io").setName("User Two").build());
    return developers;
  }
}