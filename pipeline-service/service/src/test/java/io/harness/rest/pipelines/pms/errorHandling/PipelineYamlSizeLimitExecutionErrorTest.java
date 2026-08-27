/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rest.pipelines.pms.errorHandling;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.pipeline.PipelineEntityUtils.PIPELINE_YAML_SIZE_LIMIT_EXCEEDED_MESSAGE;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.validation.service.intfc.PipelineValidationService;
import io.harness.pms.plan.execution.helper.UnifiedPipelineExecutionUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import java.io.IOException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;

@OwnedBy(PIPELINE)
public class PipelineYamlSizeLimitExecutionErrorTest extends PipelineServiceTestBase {
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock PipelineValidationService pipelineValidationService;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Spy @InjectMocks PMSPipelineServiceHelper pmsPipelineServiceHelper;

  private static final String YAML_SIZE_LIMIT_SNAKEYAML_MESSAGE = "The incoming YAML document exceeds the limit";

  private PipelineEntity buildPipelineEntity() {
    return PipelineEntity.builder()
        .accountId("account")
        .orgIdentifier("org")
        .projectIdentifier("project")
        .identifier("pipeline")
        .yaml("pipeline:\n  name: test\n  identifier: pipeline")
        .harnessVersion(HarnessYamlVersion.V0)
        .build();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void tc03TemplateExpansionOver3MbReturnsNewMessage() {
    PipelineEntity pipelineEntity = buildPipelineEntity();

    TemplateMergeResponseDTO templateMergeResponse =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml("large: yaml").build();

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier("account")
                              .orgIdentifier("org")
                              .projectIdentifier("project")
                              .scopeType(ScopeLevel.PROJECT)
                              .uniqueId("uniqueId")
                              .build();

    when(pipelineTemplateHelper.resolveTemplateRefsInPipeline(
             any(PipelineEntity.class), any(ScopeInfo.class), anyBoolean(), anyBoolean()))
        .thenReturn(templateMergeResponse);

    IOException snakeYamlSizeCause = new IOException(YAML_SIZE_LIMIT_SNAKEYAML_MESSAGE);
    InvalidRequestException sizeExceededException =
        new InvalidRequestException("The YAML document exceeds the maximum allowed size limit", snakeYamlSizeCause);
    when(pipelineValidationService.validateYamlAndGetGovernanceMetadata(
             anyString(), anyString(), anyString(), anyString(), nullable(String.class), any(PipelineEntity.class)))
        .thenThrow(sizeExceededException);

    assertThatThrownBy(()
                           -> pmsPipelineServiceHelper.resolveTemplatesAndValidatePipeline(
                               pipelineEntity, false, false, scopeInfo, true, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(PIPELINE_YAML_SIZE_LIMIT_EXCEEDED_MESSAGE);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void tc06V1StagesExecutionPathOver3MbReturnsNewMessage() {
    IOException snakeYamlSizeCause = new IOException(YAML_SIZE_LIMIT_SNAKEYAML_MESSAGE);

    try (MockedStatic<YamlUtils> yamlUtilsMock = Mockito.mockStatic(YamlUtils.class)) {
      yamlUtilsMock.when(() -> YamlUtils.read(anyString(), any(Class.class))).thenThrow(snakeYamlSizeCause);
      yamlUtilsMock.when(() -> YamlUtils.isYamlSizeLimitExceeded(any(Throwable.class))).thenReturn(true);

      assertThatThrownBy(() -> UnifiedPipelineExecutionUtils.getUnifiedPipeline("pipeline:\n  name: test"))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining(PIPELINE_YAML_SIZE_LIMIT_EXCEEDED_MESSAGE);

      assertThatThrownBy(() -> UnifiedPipelineExecutionUtils.shouldAllowStageExecutions("pipeline:\n  name: test"))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining(PIPELINE_YAML_SIZE_LIMIT_EXCEEDED_MESSAGE);
    }
  }
}
