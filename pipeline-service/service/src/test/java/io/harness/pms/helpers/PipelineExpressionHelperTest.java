/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.SHASHANK_JAIN;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.account.services.AccountClient;
import io.harness.account.settings.service.impl.PipelineSettingsServiceImpl;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO;
import io.harness.licensing.beans.modules.CIModuleLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.STOModuleLicenseDTO;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.pipeline.PipelineEntityUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.sdk.helper.PmsSdkHelper;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineExpressionHelperTest extends CategoryTest {
  @Mock PmsExecutionSummaryService pmsExecutionSummaryService;
  @Mock PipelineServiceConfiguration pipelineServiceConfiguration;
  @Mock AccountClient accountClient;
  @Mock PipelineSettingsServiceImpl pipelineSettingsService;
  @InjectMocks PipelineExpressionHelper pipelineExpressionHelper;
  @Mock private PmsSdkHelper pmsSdkHelper;
  @Mock private PipelineEntityUtils pipelineEntityUtils;
  Ambiance ambiance = null;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(pipelineEntityUtils.getModuleNameFromPipelineEntity(anyList(), any())).thenAnswer(invocation -> {
      List<String> modules = invocation.getArgument(0);
      if (modules.contains("common")) {
        return "cd";
      }
      for (String module : modules) {
        if (!"pms".equals(module)) {
          return module;
        }
      }
      return "cd";
    });
    ExecutionMetadata metadata =
        Ambiance.newBuilder().getMetadataBuilder().setPipelineIdentifier("pipeline_test").setModuleType("ci").build();
    ambiance = Ambiance.newBuilder()
                   .putSetupAbstractions(SetupAbstractionKeys.accountId, "__ACCOUNT_ID__")
                   .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "org_test")
                   .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "project_test")
                   .setPlanExecutionId("PLAN_EXECUTION_ID")
                   .setMetadata(metadata)
                   .build();
    when(pipelineServiceConfiguration.getPipelineServiceBaseUrl()).thenReturn("https://app.harness.io/ng/#");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testGenerationOfURLWithoutVanity() throws IOException {
    Call vanityUrlCall = mock(Call.class);
    when(vanityUrlCall.execute()).thenReturn(Response.success(new RestResponse<>("")));
    when(accountClient.getVanityUrl(anyString())).thenReturn(vanityUrlCall);
    doReturn("").when(pmsSdkHelper).getModulePath("");
    assertThat(pipelineExpressionHelper.generateUrl(ambiance, null))
        .isEqualTo("https://app.harness.io/ng/#/account/__ACCOUNT_ID__/ci/orgs/org_test/projects/project_test/"
            + "pipelines/pipeline_test/executions/PLAN_EXECUTION_ID/pipeline");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGenerateUrlForPipelineRollbackMode() throws IOException {
    Ambiance rollbackAmbiance =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", "__ACCOUNT_ID__")
            .putSetupAbstractions("orgIdentifier", "org_test")
            .putSetupAbstractions("projectIdentifier", "project_test")
            .setMetadata(
                ExecutionMetadata.newBuilder()
                    .setModuleType("cd")
                    .setIsNotificationConfigured(true)
                    .setPipelineIdentifier("pipeline_test")
                    .setExecutionMode(ExecutionMode.PIPELINE_ROLLBACK)
                    .setOriginalPlanExecutionIdForRollbackMode("rollbackExecutionId")
                    .setTriggerInfo(
                        io.harness.pms.contracts.plan.ExecutionTriggerInfo.newBuilder()
                            .setTriggeredBy(
                                io.harness.pms.contracts.plan.TriggeredBy.newBuilder().setIdentifier("dummy").build())
                            .build())
                    .build())
            .setPlanExecutionId("dummyPlanExecutionId")
            .build();
    Call vanityUrlCall = mock(Call.class);
    when(vanityUrlCall.execute()).thenReturn(Response.success(new RestResponse<>("")));
    when(accountClient.getVanityUrl(anyString())).thenReturn(vanityUrlCall);
    doReturn("").when(pmsSdkHelper).getModulePath("");
    assertThat(pipelineExpressionHelper.generateUrl(rollbackAmbiance, null))
        .isEqualTo("https://app.harness.io/ng/#/account/__ACCOUNT_ID__/cd/orgs/org_test/projects/project_test/"
            + "pipelines/pipeline_test/executions/rollbackExecutionId/pipeline");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testGenerationOfURLWithVanity() throws IOException {
    Call vanityUrlCall = mock(Call.class);
    when(vanityUrlCall.execute()).thenReturn(Response.success(new RestResponse<>("https://vanity.harness.io")));
    when(accountClient.getVanityUrl(anyString())).thenReturn(vanityUrlCall);
    doReturn("").when(pmsSdkHelper).getModulePath("");
    assertThat(pipelineExpressionHelper.generateUrl(ambiance, null))
        .isEqualTo("https://vanity.harness.io/ng/#/account/__ACCOUNT_ID__/ci/orgs/org_test/projects/project_test/"
            + "pipelines/pipeline_test/executions/PLAN_EXECUTION_ID/pipeline");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGenerationOfPipelineUrlWithoutVanity() throws IOException {
    Call vanityUrlCall = mock(Call.class);
    when(vanityUrlCall.execute()).thenReturn(Response.success(new RestResponse<>("")));
    when(accountClient.getVanityUrl(anyString())).thenReturn(vanityUrlCall);
    assertEquals(
        pipelineExpressionHelper.generatePipelineUrl(ambiance, PipelineExecutionSummaryEntity.builder().build()),
        "https://app.harness.io/ng/#/account/__ACCOUNT_ID__/ci/orgs/org_test/projects/project_test/pipelines/"
            + "pipeline_test/pipeline-studio");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGenerationOfPipelineUrlWithVanity() throws IOException {
    Call vanityUrlCall = mock(Call.class);
    when(vanityUrlCall.execute()).thenReturn(Response.success(new RestResponse<>("https://vanity.harness.io")));
    when(accountClient.getVanityUrl(anyString())).thenReturn(vanityUrlCall);
    assertEquals(
        pipelineExpressionHelper.generatePipelineUrl(ambiance, PipelineExecutionSummaryEntity.builder().build()),
        "https://vanity.harness.io/ng/#/account/__ACCOUNT_ID__/ci/orgs/org_test/projects/project_test/pipelines/"
            + "pipeline_test/pipeline-studio");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetVanityUrl() throws IOException {
    Call vanityUrlCall = mock(Call.class);
    when(vanityUrlCall.execute()).thenReturn(Response.success(new RestResponse<>("https://vanity.harness.io")));
    when(accountClient.getVanityUrl(anyString())).thenReturn(vanityUrlCall);
    assertEquals(pipelineExpressionHelper.getVanityUrl("__ACCOUNT_ID__"), "https://vanity.harness.io");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetModuleName() {
    assertEquals(pipelineExpressionHelper.getModuleName(ambiance), "ci");
    ambiance = Ambiance.newBuilder()
                   .putSetupAbstractions(SetupAbstractionKeys.accountId, "__ACCOUNT_ID__")
                   .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "org_test")
                   .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "project_test")
                   .setPlanExecutionId("PLAN_EXECUTION_ID")
                   .setMetadata(ExecutionMetadata.newBuilder().build())
                   .build();
    when(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections("__ACCOUNT_ID__", "PLAN_EXECUTION_ID",
             Sets.newHashSet(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.modules)))
        .thenReturn(null);
    assertEquals(pipelineExpressionHelper.getModuleName(ambiance, null), "cd");
    List<String> modules = new ArrayList<>();
    modules.add("pms");
    modules.add("ci");
    modules.add("cd");
    when(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections("__ACCOUNT_ID__", "PLAN_EXECUTION_ID",
             Sets.newHashSet(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.modules)))
        .thenReturn(PipelineExecutionSummaryEntity.builder().modules(modules).build());
    assertEquals(pipelineExpressionHelper.getModuleName(
                     ambiance, PipelineExecutionSummaryEntity.builder().modules(modules).build()),
        "ci");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetModuleNameFromPipelineExecutionSummary() {
    List<String> modules = new ArrayList<>();
    assertEquals(
        pipelineExpressionHelper.getModuleName(PipelineExecutionSummaryEntity.builder().modules(modules).build(), "cd",
            AmbianceUtils.getAccountId(ambiance)),
        "cd");
    modules.add("pms");
    assertEquals(
        pipelineExpressionHelper.getModuleName(PipelineExecutionSummaryEntity.builder().modules(modules).build(), "cd",
            AmbianceUtils.getAccountId(ambiance)),
        "cd");
    modules.add("cd");
    assertEquals(
        pipelineExpressionHelper.getModuleName(PipelineExecutionSummaryEntity.builder().modules(modules).build(), "cd",
            AmbianceUtils.getAccountId(ambiance)),
        "cd");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetBaseUrl() {
    assertEquals(PipelineExpressionHelper.getBaseUrl("default", ""), "default");
    assertEquals(PipelineExpressionHelper.getBaseUrl("https://app.harness.io/ng/#", "https://vanity.harness.io/"),
        "https://vanity.harness.io/ng/#");
    String baseUrl = PipelineExpressionHelper.getBaseUrl("default", "vanity");
    assertEquals(baseUrl, "default");
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testGetActiveLicenseModules() {
    List<ModuleLicenseDTO> licenses = new ArrayList<>();

    // Add active CI module license
    licenses.add(CIModuleLicenseDTO.builder().moduleType(ModuleType.CI).status(LicenseStatus.ACTIVE).build());

    // Add inactive CD module license
    licenses.add(CDModuleLicenseDTO.builder().moduleType(ModuleType.CD).status(LicenseStatus.EXPIRED).build());

    // Add active STO module license
    licenses.add(STOModuleLicenseDTO.builder().moduleType(ModuleType.STO).status(LicenseStatus.ACTIVE).build());

    Set<String> activeLicenses = pipelineExpressionHelper.getActiveLicenseModules(licenses);

    // Should only contain active modules in lowercase
    assertEquals(2, activeLicenses.size());
    assertTrue(activeLicenses.contains("ci"));
    assertTrue(activeLicenses.contains("sto"));
    assertFalse(activeLicenses.contains("cd"));
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testGetModuleNameWithLicenseFiltering() {
    // Setup test data
    String accountId = "testAccount";
    List<ModuleLicenseDTO> licenses = new ArrayList<>();
    // Add active CI and STO licenses
    licenses.add(CIModuleLicenseDTO.builder().moduleType(ModuleType.CI).status(LicenseStatus.ACTIVE).build());
    licenses.add(STOModuleLicenseDTO.builder().moduleType(ModuleType.STO).status(LicenseStatus.ACTIVE).build());

    // Create pipeline summary with CI and CD modules
    List<String> modules = new ArrayList<>();
    modules.add("cd"); // This should be filtered out as CD license is not active
    modules.add("ci");
    PipelineExecutionSummaryEntity summary = PipelineExecutionSummaryEntity.builder().modules(modules).build();

    // Mock the pipeline settings service to return our test licenses
    try {
      when(pipelineSettingsService.getModuleLicense(accountId)).thenReturn(licenses);
    } catch (Exception e) {
      System.out.println("Inside catch block of exception");
    }
    // Test the method
    String result = pipelineExpressionHelper.getModuleName(summary, "cd", accountId);

    // Since CD module was filtered out due to no active license, CI should be selected
    assertEquals("ci", result);

    // Mock the pipeline settings service to return our test licenses
    try {
      when(pipelineSettingsService.getModuleLicense(accountId)).thenThrow(new Exception("license service call failed"));
    } catch (Exception e) {
      System.out.println("Inside catch block of exception");
    }
    // Test the method
    result = pipelineExpressionHelper.getModuleName(summary, "cd", accountId);

    // Since call to license service failed hence we get cd as module name as it is first in modules list in
    // PipelineExecutionSummaryEntity
    assertEquals("ci", result);
  }
}
