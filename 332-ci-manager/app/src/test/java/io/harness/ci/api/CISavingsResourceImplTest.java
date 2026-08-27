/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.ci.api.AnnotationUtils.assertParameterCounts;
import static io.harness.cimanager.savings.api.CISavingsResource.PIPELINE_RESOURCE_TYPE;
import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.savings.api.SavingsInfo;
import io.harness.category.element.UnitTests;
import io.harness.ci.api.CISavingsResourceImpl;
import io.harness.ci.savings.CISavingsService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.rule.Owner;

import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CI)
public class CISavingsResourceImplTest {
  @Mock private CISavingsService ciSavingsService;
  @InjectMocks private CISavingsResourceImpl ciSavingsResource;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetStageSavingsAnnotations() throws NoSuchMethodException {
    // Check method level annotations for getBuildHealth method
    Method getStageSavingsMethod = CISavingsResourceImpl.class.getDeclaredMethod(
        "getStageSavings", String.class, String.class, String.class, String.class, String.class);
    assertTrue(getStageSavingsMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(
        PIPELINE_RESOURCE_TYPE, getStageSavingsMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(PipelineRbacPermissions.PIPELINE_VIEW,
        getStageSavingsMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(getStageSavingsMethod, 1, AccountIdentifier.class);
    assertParameterCounts(getStageSavingsMethod, 1, OrgIdentifier.class);
    assertParameterCounts(getStageSavingsMethod, 1, ProjectIdentifier.class);
    assertParameterCounts(getStageSavingsMethod, 1, ResourceIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetStageSavings() {
    String accountIdentifier = "testAccount";
    String orgIdentifier = "testOrg";
    String projectIdentifier = "testProject";
    String pipelineIdentifier = "testPipeline";
    String stageExecutionId = "testStage";

    SavingsInfo savingsInfo = SavingsInfo.builder().optimizationState("OPTIMIZED").timeSaved(1000L).build();

    when(ciSavingsService.getStageSavings(accountIdentifier, stageExecutionId)).thenReturn(savingsInfo);

    ResponseDTO<SavingsInfo> response = ciSavingsResource.getStageSavings(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, stageExecutionId);

    assertEquals(savingsInfo, response.getData());
    verify(ciSavingsService, times(1)).getStageSavings(accountIdentifier, stageExecutionId);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetFirstFullRunAnnotations() throws NoSuchMethodException {
    // Check method level annotations for getBuildHealth method
    Method getStageSavingsMethod = CISavingsResourceImpl.class.getDeclaredMethod(
        "getFirstFullRun", String.class, String.class, String.class, String.class);
    assertTrue(getStageSavingsMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(
        PIPELINE_RESOURCE_TYPE, getStageSavingsMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(PipelineRbacPermissions.PIPELINE_VIEW,
        getStageSavingsMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(getStageSavingsMethod, 1, AccountIdentifier.class);
    assertParameterCounts(getStageSavingsMethod, 1, OrgIdentifier.class);
    assertParameterCounts(getStageSavingsMethod, 1, ProjectIdentifier.class);
    assertParameterCounts(getStageSavingsMethod, 1, ResourceIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetFirstFullRun() {
    String accountIdentifier = "testAccount";
    String orgIdentifier = "testOrg";
    String projectIdentifier = "testProject";
    String pipelineIdentifier = "testPipeline";
    String planExecutionId = "testPlanExecutionId";

    when(ciSavingsService.getFirstFullRun(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier))
        .thenReturn(planExecutionId);

    ResponseDTO<String> response =
        ciSavingsResource.getFirstFullRun(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);

    assertEquals(planExecutionId, response.getData());
    verify(ciSavingsService, times(1))
        .getFirstFullRun(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);
  }
}
