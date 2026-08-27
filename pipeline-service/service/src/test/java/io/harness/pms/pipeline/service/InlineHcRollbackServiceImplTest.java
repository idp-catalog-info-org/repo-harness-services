/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.rule.OwnerRule.BHUMIJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.ngpipeline.inputset.beans.resource.RollbackResponse;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetInlineHcMigrationService;
import io.harness.pms.pipeline.ConsolidatedRollbackResponse;
import io.harness.pms.pipeline.InlineHcMigrationEntityType;
import io.harness.pms.pipeline.service.intfc.PMSPipelineInlineHcMigrationService;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.resources.beans.RollbackResponseDTO;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class InlineHcRollbackServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account123";

  @Mock private PMSPipelineInlineHcMigrationService pmsPipelineInlineHcMigrationService;
  @Mock private PMSInputSetInlineHcMigrationService pmsInputSetInlineHcMigrationService;
  @Mock private TemplateResourceClient templateResourceClient;

  private InlineHcRollbackServiceImpl inlineHcRollbackService;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    inlineHcRollbackService = new InlineHcRollbackServiceImpl(
        pmsPipelineInlineHcMigrationService, pmsInputSetInlineHcMigrationService, templateResourceClient);
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testRollbackFromInlineHCToInline_AllEntities_Success() {
    // Mock pipeline rollback
    io.harness.pms.pipeline.RollbackResponse pipelineResponse =
        io.harness.pms.pipeline.RollbackResponse.builder().migratedCount(10L).build();
    when(pmsPipelineInlineHcMigrationService.rollbackPipelinesFromInlineHCToInline(ACCOUNT_ID))
        .thenReturn(pipelineResponse);

    // Mock input set rollback
    RollbackResponse inputSetResponse = RollbackResponse.builder().migratedCount(5L).build();
    when(pmsInputSetInlineHcMigrationService.rollbackInputSetsFromInlineHCToInline(ACCOUNT_ID))
        .thenReturn(inputSetResponse);

    // Mock template rollback
    RollbackResponseDTO templateResponse = RollbackResponseDTO.builder().migratedCount(3L).build();
    try (MockedStatic<NGRestUtils> ngRestUtilsMock = Mockito.mockStatic(NGRestUtils.class)) {
      ngRestUtilsMock.when(() -> NGRestUtils.getResponse(templateResourceClient.rollbackInlineHCToInline(ACCOUNT_ID)))
          .thenReturn(templateResponse);

      ConsolidatedRollbackResponse response =
          inlineHcRollbackService.rollbackFromInlineHCToInline(ACCOUNT_ID, InlineHcMigrationEntityType.ALL);

      assertThat(response).isNotNull();
      assertThat(response.getPipelineMigratedCount()).isEqualTo(10L);
      assertThat(response.getInputSetMigratedCount()).isEqualTo(5L);
      assertThat(response.getTemplateMigratedCount()).isEqualTo(3L);
      assertThat(response.getErrors()).isEmpty();

      verify(pmsPipelineInlineHcMigrationService).rollbackPipelinesFromInlineHCToInline(ACCOUNT_ID);
      verify(pmsInputSetInlineHcMigrationService).rollbackInputSetsFromInlineHCToInline(ACCOUNT_ID);
    }
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testRollbackFromInlineHCToInline_PipelineOnly() {
    io.harness.pms.pipeline.RollbackResponse pipelineResponse =
        io.harness.pms.pipeline.RollbackResponse.builder().migratedCount(10L).build();
    when(pmsPipelineInlineHcMigrationService.rollbackPipelinesFromInlineHCToInline(ACCOUNT_ID))
        .thenReturn(pipelineResponse);

    ConsolidatedRollbackResponse response =
        inlineHcRollbackService.rollbackFromInlineHCToInline(ACCOUNT_ID, InlineHcMigrationEntityType.PIPELINE);

    assertThat(response).isNotNull();
    assertThat(response.getPipelineMigratedCount()).isEqualTo(10L);
    assertThat(response.getInputSetMigratedCount()).isEqualTo(0L);
    assertThat(response.getTemplateMigratedCount()).isEqualTo(0L);
    assertThat(response.getErrors()).isEmpty();

    verify(pmsPipelineInlineHcMigrationService).rollbackPipelinesFromInlineHCToInline(ACCOUNT_ID);
    verifyNoInteractions(pmsInputSetInlineHcMigrationService);
    verifyNoInteractions(templateResourceClient);
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testRollbackFromInlineHCToInline_HandleErrors() {
    when(pmsPipelineInlineHcMigrationService.rollbackPipelinesFromInlineHCToInline(ACCOUNT_ID))
        .thenThrow(new RuntimeException("Pipeline service error"));
    when(pmsInputSetInlineHcMigrationService.rollbackInputSetsFromInlineHCToInline(ACCOUNT_ID))
        .thenThrow(new RuntimeException("Input set service error"));

    try (MockedStatic<NGRestUtils> ngRestUtilsMock = Mockito.mockStatic(NGRestUtils.class)) {
      ngRestUtilsMock.when(() -> NGRestUtils.getResponse(templateResourceClient.rollbackInlineHCToInline(ACCOUNT_ID)))
          .thenThrow(new RuntimeException("Template service error"));

      ConsolidatedRollbackResponse response =
          inlineHcRollbackService.rollbackFromInlineHCToInline(ACCOUNT_ID, InlineHcMigrationEntityType.ALL);

      assertThat(response).isNotNull();
      assertThat(response.getPipelineMigratedCount()).isEqualTo(0L);
      assertThat(response.getInputSetMigratedCount()).isEqualTo(0L);
      assertThat(response.getTemplateMigratedCount()).isEqualTo(0L);
      assertThat(response.getErrors()).hasSize(3);
      assertThat(response.getErrors())
          .contains("Error while rolling back pipelines. Error: Pipeline service error",
              "Error while rolling back input sets. Error: Input set service error",
              "Error while rolling back templates. Error: Template service error");
    }
  }
}
