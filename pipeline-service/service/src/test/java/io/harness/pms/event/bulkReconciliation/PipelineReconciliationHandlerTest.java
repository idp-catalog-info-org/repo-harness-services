/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.bulkReconciliation;

import static io.harness.rule.OwnerRule.SHIVAM;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.template.refresh.ReferenceEntityDetails;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.pms.rbac.PipelineSplitPermissionsHelper;
import io.harness.pms.template.service.PipelineRefreshService;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class PipelineReconciliationHandlerTest extends CategoryTest {
  @Mock PipelineRefreshService pipelineRefreshService;
  @Mock TemplateResourceClient templateResourceClient;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  @InjectMocks private PipelineReconciliationHandler handler;

  private final String accountId = "accId";
  private final String orgId = "orgId";
  private final String projectId = "projId";
  private final String parentUniqueId = "parentUniqueId";
  private final String pipelineId = "pipeId";
  private final String bulkReconciliationUUID = "bulkId";
  private MockedStatic<NGRestUtils> mockSettings;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(scopeResolutionHelper.getScopeInfo(any(), any()))
        .thenReturn(ScopeInfo.builder().uniqueId("unique-id").build());
    handler = PipelineReconciliationHandler.builder()
                  .accountIdentifier(accountId)
                  .orgIdentifier(orgId)
                  .projectIdentifier(projectId)
                  .parentUniqueId(parentUniqueId)
                  .pipelineIdentifier(pipelineId)
                  .pipelineRefreshService(pipelineRefreshService)
                  .referenceEntityDetails(ReferenceEntityDetails.builder().checkForReconciliation(true).build())
                  .bulkReconciliationUUID(bulkReconciliationUUID)
                  .pmsFeatureFlagService(pmsFeatureFlagService)
                  .pipelineSplitPermissionsHelper(pipelineSplitPermissionsHelper)
                  .templateResourceClient(templateResourceClient)
                  .scopeResolutionHelper(scopeResolutionHelper)
                  .build();
    mockSettings = Mockito.mockStatic(NGRestUtils.class);
  }

  @After
  public void teardown() {
    mockSettings.close();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPipelineReconciliationHandler() {
    PipelineReconciliationHandler pipelineReconciliationHandler =
        PipelineReconciliationHandler.builder()
            .accountIdentifier("accountId")
            .orgIdentifier("org")
            .projectIdentifier("projectIdentifier")
            .pipelineIdentifier("identifier")
            .pipelineRefreshService(pipelineRefreshService)
            .bulkReconciliationUUID("uuid")
            .templateResourceClient(templateResourceClient)
            .referenceEntityDetails(ReferenceEntityDetails.builder().build())
            .pmsFeatureFlagService(pmsFeatureFlagService)
            .pipelineSplitPermissionsHelper(pipelineSplitPermissionsHelper)
            .scopeResolutionHelper(scopeResolutionHelper)
            .build();

    mockSettings.when(NGRestUtils.getResponse(any())).thenReturn(true);
    pipelineReconciliationHandler.run();
    mockSettings.verify(() -> NGRestUtils.getResponse(any()), Mockito.times(2));
    verify(pipelineRefreshService, times(1))
        .recursivelyRefreshAllTemplateInputsInPipelineAndUpdateReconcileEvent(
            any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPipelineReconciliationHandlerOnFailure() {
    PipelineReconciliationHandler pipelineReconciliationHandler = PipelineReconciliationHandler.builder()
                                                                      .accountIdentifier("accountId")
                                                                      .orgIdentifier("org")
                                                                      .projectIdentifier("projectIdentifier")
                                                                      .pipelineIdentifier("identifier")
                                                                      .pipelineRefreshService(pipelineRefreshService)
                                                                      .bulkReconciliationUUID("uuid")
                                                                      .templateResourceClient(templateResourceClient)
                                                                      .build();
    pipelineReconciliationHandler.run();
    mockSettings.verify(() -> NGRestUtils.getResponse(any()), Mockito.times(1));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPipelineReconciliationHandlerReconcileCheck() {
    PipelineReconciliationHandler pipelineReconciliationHandler =
        PipelineReconciliationHandler.builder()
            .accountIdentifier("accountId")
            .orgIdentifier("org")
            .projectIdentifier("projectIdentifier")
            .pipelineIdentifier("identifier")
            .pipelineRefreshService(pipelineRefreshService)
            .bulkReconciliationUUID("uuid")
            .templateResourceClient(templateResourceClient)
            .pmsFeatureFlagService(pmsFeatureFlagService)
            .pipelineSplitPermissionsHelper(pipelineSplitPermissionsHelper)
            .referenceEntityDetails(ReferenceEntityDetails.builder().checkForReconciliation(true).build())
            .scopeResolutionHelper(scopeResolutionHelper)
            .build();
    ValidateTemplateInputsResponseDTO response = mock(ValidateTemplateInputsResponseDTO.class);
    when(response.isValidYaml()).thenReturn(false);
    pipelineReconciliationHandler.run();
    verify(pipelineRefreshService, times(1)).validateTemplateInputsInPipeline(any(), any(), any(), any(), any(), any());
    verify(pipelineRefreshService, never())
        .recursivelyRefreshAllTemplateInputsInPipelineAndUpdateReconcileEvent(
            any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldReturnTrue_whenYamlIsInvalidAndCheckEnabled() {
    ValidateTemplateInputsResponseDTO response = mock(ValidateTemplateInputsResponseDTO.class);
    when(response.isValidYaml()).thenReturn(false);
    when(pipelineRefreshService.validateTemplateInputsInPipeline(
             accountId, orgId, projectId, pipelineId, "false", ScopeInfo.builder().uniqueId("unique-id").build()))
        .thenReturn(response);
    boolean result = handler.shouldPerformReconciliationCheck();

    assert (result);
    verify(templateResourceClient, times(1))
        .updateBulkReconcileStatus(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq("PIPELINE"),
            eq(bulkReconciliationUUID), eq("OUT_OF_SYNC"), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldReturnTrue_whenYamlIsValid() {
    ValidateTemplateInputsResponseDTO response = mock(ValidateTemplateInputsResponseDTO.class);
    when(response.isValidYaml()).thenReturn(true);
    when(
        pipelineRefreshService.validateTemplateInputsInPipeline(accountId, orgId, projectId, pipelineId, "false", null))
        .thenReturn(response);

    boolean result = handler.shouldPerformReconciliationCheck();
    assertTrue(result);
    verify(templateResourceClient, never())
        .updateBulkReconcileStatus(any(), any(), any(), any(), any(), any(), any(), any());
  }
}
