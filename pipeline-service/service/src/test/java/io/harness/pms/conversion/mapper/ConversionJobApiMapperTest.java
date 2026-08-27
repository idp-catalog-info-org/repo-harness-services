/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.mapper;

import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.goconvert.EntityType;
import io.harness.pms.conversion.beans.ConversionActionType;
import io.harness.pms.conversion.beans.ConversionErrorDetail;
import io.harness.pms.conversion.beans.ConversionJobEntity;
import io.harness.pms.conversion.beans.ConversionJobMetricsDTO;
import io.harness.pms.conversion.beans.ConversionNodeSummary;
import io.harness.pms.conversion.beans.ConversionStatus;
import io.harness.pms.conversion.beans.ErrorSeverity;
import io.harness.pms.conversion.beans.PipelineConversionMetricsDTO;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.ConversionJobRequestBody;
import io.harness.spec.server.pipeline.v1.model.ConversionJobResponseBody;
import io.harness.spec.server.pipeline.v1.model.EntityIdentifier;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ConversionJobApiMapperTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account123";
  private static final String ORG_ID = "org1";
  private static final String PROJECT_ID = "project1";

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testToEntityForSinglePipeline() {
    ConversionJobRequestBody requestBody = new ConversionJobRequestBody();
    requestBody.setActionType(ConversionJobRequestBody.ActionTypeEnum.SINGLE);
    requestBody.setEntityType(ConversionJobRequestBody.EntityTypeEnum.PIPELINE);
    requestBody.setOrgId(ORG_ID);
    requestBody.setProjectId(PROJECT_ID);

    EntityIdentifier entityRef = new EntityIdentifier();
    entityRef.setEntityId("myPipeline");
    entityRef.setEntityType(EntityIdentifier.EntityTypeEnum.PIPELINE);
    entityRef.setBranch("main");
    requestBody.setEntityReference(entityRef);

    ConversionJobEntity entity = ConversionJobApiMapper.toEntity(ACCOUNT_ID, requestBody);

    assertThat(entity.getStatus()).isEqualTo(ConversionStatus.QUEUED);
    assertThat(entity.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(entity.getOrgId()).isEqualTo(ORG_ID);
    assertThat(entity.getProjectId()).isEqualTo(PROJECT_ID);
    assertThat(entity.getActionType()).isEqualTo(ConversionActionType.SINGLE);
    assertThat(entity.getEntityType()).isEqualTo(EntityType.PIPELINE);
    assertThat(entity.getEntityIdentifier()).isEqualTo("myPipeline");
    assertThat(entity.getEntityReference()).isNotNull();
    assertThat(entity.getEntityReference().getEntityId()).isEqualTo("myPipeline");
    assertThat(entity.getEntityReference().getBranch()).isEqualTo("main");
    assertThat(entity.getForceReconvert()).isFalse();
    assertThat(entity.getConversionMetrics()).isNotNull();
    assertThat(entity.getConversionMetrics().getTotalEntities()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testToEntityForBatchWithMultipleReferences() {
    ConversionJobRequestBody requestBody = new ConversionJobRequestBody();
    requestBody.setActionType(ConversionJobRequestBody.ActionTypeEnum.BATCH);
    requestBody.setEntityType(ConversionJobRequestBody.EntityTypeEnum.PIPELINE);
    requestBody.setOrgId(ORG_ID);
    requestBody.setProjectId(PROJECT_ID);

    EntityIdentifier ref1 = new EntityIdentifier();
    ref1.setEntityId("pipeline1");
    ref1.setEntityType(EntityIdentifier.EntityTypeEnum.PIPELINE);

    EntityIdentifier ref2 = new EntityIdentifier();
    ref2.setEntityId("pipeline2");
    ref2.setEntityType(EntityIdentifier.EntityTypeEnum.PIPELINE);

    requestBody.setEntityReferences(Arrays.asList(ref1, ref2));

    ConversionJobEntity entity = ConversionJobApiMapper.toEntity(ACCOUNT_ID, requestBody);

    assertThat(entity.getActionType()).isEqualTo(ConversionActionType.BATCH);
    assertThat(entity.getEntityIdentifier()).isNull();
    assertThat(entity.getEntityReference()).isNull();
    assertThat(entity.getEntityReferences()).hasSize(2);
    assertThat(entity.getEntityReferences().get(0).getEntityId()).isEqualTo("pipeline1");
    assertThat(entity.getEntityReferences().get(1).getEntityId()).isEqualTo("pipeline2");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testToEntityForProjectAction() {
    ConversionJobRequestBody requestBody = new ConversionJobRequestBody();
    requestBody.setActionType(ConversionJobRequestBody.ActionTypeEnum.PROJECT);
    requestBody.setOrgId(ORG_ID);
    requestBody.setProjectId(PROJECT_ID);

    ConversionJobEntity entity = ConversionJobApiMapper.toEntity(ACCOUNT_ID, requestBody);

    assertThat(entity.getActionType()).isEqualTo(ConversionActionType.PROJECT);
    assertThat(entity.getEntityType()).isEqualTo(EntityType.PIPELINE);
    assertThat(entity.getEntityIdentifier()).isNull();
    assertThat(entity.getEntityReference()).isNull();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testToEntityWithForceReconvert() {
    ConversionJobRequestBody requestBody = new ConversionJobRequestBody();
    requestBody.setActionType(ConversionJobRequestBody.ActionTypeEnum.SINGLE);
    requestBody.setEntityType(ConversionJobRequestBody.EntityTypeEnum.PIPELINE);
    requestBody.setOrgId(ORG_ID);
    requestBody.setProjectId(PROJECT_ID);
    requestBody.setForceReconvert(true);

    EntityIdentifier entityRef = new EntityIdentifier();
    entityRef.setEntityId("myPipeline");
    entityRef.setEntityType(EntityIdentifier.EntityTypeEnum.PIPELINE);
    requestBody.setEntityReference(entityRef);

    ConversionJobEntity entity = ConversionJobApiMapper.toEntity(ACCOUNT_ID, requestBody);

    assertThat(entity.getForceReconvert()).isTrue();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testToResponseBodyForSingleJob() {
    ConversionJobEntity entity = ConversionJobEntity.builder()
                                     .uuid("job-123")
                                     .status(ConversionStatus.SUCCESS)
                                     .actionType(ConversionActionType.SINGLE)
                                     .entityType(EntityType.PIPELINE)
                                     .accountId(ACCOUNT_ID)
                                     .orgId(ORG_ID)
                                     .projectId(PROJECT_ID)
                                     .entityIdentifier("myPipeline")
                                     .v1Identifier("myPipeline_a3f2")
                                     .retryCount(0)
                                     .createdAt(1000L)
                                     .startTs(2000L)
                                     .endTs(3000L)
                                     .conversionMetrics(ConversionJobMetricsDTO.builder()
                                                            .totalEntities(1)
                                                            .processedEntities(1)
                                                            .convertedEntities(1)
                                                            .skippedEntities(0)
                                                            .failedEntities(0)
                                                            .progressPercentage(100)
                                                            .build())
                                     .build();

    ConversionJobResponseBody response = ConversionJobApiMapper.toResponseBody(entity);

    assertThat(response.getUuid()).isEqualTo("job-123");
    assertThat(response.getStatus()).isEqualTo(ConversionJobResponseBody.StatusEnum.SUCCESS);
    assertThat(response.getActionType()).isEqualTo(ConversionJobResponseBody.ActionTypeEnum.SINGLE);
    assertThat(response.getEntityType()).isEqualTo(ConversionJobResponseBody.EntityTypeEnum.PIPELINE);
    assertThat(response.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(response.getCreatedAt()).isEqualTo(1000L);
    assertThat(response.getStartTs()).isEqualTo(2000L);
    assertThat(response.getEndTs()).isEqualTo(3000L);
    assertThat(response.getConversionMetrics()).isNotNull();
    assertThat(response.getConversionMetrics().getTotalEntities()).isEqualTo(1);
    assertThat(response.getConversionMetrics().getProgressPercentage()).isEqualTo(100);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testToResponseBodyWithConversionResults() {
    ConversionNodeSummary childNode = ConversionNodeSummary.builder()
                                          .entityIdentifier("inputSet1")
                                          .entityType(EntityType.INPUT_SET)
                                          .status(ConversionStatus.SUCCESS)
                                          .v1Identifier("inputSet1_v1")
                                          .build();

    ConversionNodeSummary rootNode = ConversionNodeSummary.builder()
                                         .entityIdentifier("myPipeline")
                                         .entityType(EntityType.PIPELINE)
                                         .status(ConversionStatus.SUCCESS)
                                         .v1Identifier("myPipeline_v1")
                                         .children(Collections.singletonList(childNode))
                                         .build();

    ConversionJobEntity entity = ConversionJobEntity.builder()
                                     .uuid("job-123")
                                     .status(ConversionStatus.SUCCESS)
                                     .actionType(ConversionActionType.SINGLE)
                                     .entityType(EntityType.PIPELINE)
                                     .accountId(ACCOUNT_ID)
                                     .conversionResults(Collections.singletonList(rootNode))
                                     .build();

    ConversionJobResponseBody response = ConversionJobApiMapper.toResponseBody(entity);

    assertThat(response.getConversionResults()).hasSize(1);
    io.harness.spec.server.pipeline.v1.model.ConversionNodeSummary resultNode = response.getConversionResults().get(0);
    assertThat(resultNode.getEntityIdentifier()).isEqualTo("myPipeline");
    assertThat(resultNode.getV1Identifier()).isEqualTo("myPipeline_v1");
    assertThat(resultNode.getStatus())
        .isEqualTo(io.harness.spec.server.pipeline.v1.model.ConversionNodeSummary.StatusEnum.SUCCESS);

    assertThat(resultNode.getChildren()).hasSize(1);
    assertThat(resultNode.getChildren().get(0).getEntityIdentifier()).isEqualTo("inputSet1");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testToResponseBodyWithPipelineMetricsOnNode() {
    PipelineConversionMetricsDTO pipelineMetrics = PipelineConversionMetricsDTO.builder()
                                                       .totalInputSets(3)
                                                       .convertedInputSets(2)
                                                       .skippedInputSets(0)
                                                       .failedInputSets(1)
                                                       .totalTemplates(1)
                                                       .convertedTemplates(1)
                                                       .skippedTemplates(0)
                                                       .failedTemplates(0)
                                                       .totalTriggers(2)
                                                       .convertedTriggers(2)
                                                       .skippedTriggers(0)
                                                       .failedTriggers(0)
                                                       .build();

    ConversionNodeSummary pipelineNode = ConversionNodeSummary.builder()
                                             .entityIdentifier("myPipeline")
                                             .entityType(EntityType.PIPELINE)
                                             .status(ConversionStatus.PARTIAL_SUCCESS)
                                             .pipelineMetrics(pipelineMetrics)
                                             .build();

    ConversionJobEntity entity = ConversionJobEntity.builder()
                                     .uuid("pipeline-job")
                                     .status(ConversionStatus.PARTIAL_SUCCESS)
                                     .actionType(ConversionActionType.SINGLE)
                                     .entityType(EntityType.PIPELINE)
                                     .accountId(ACCOUNT_ID)
                                     .conversionResults(Collections.singletonList(pipelineNode))
                                     .build();

    ConversionJobResponseBody response = ConversionJobApiMapper.toResponseBody(entity);

    assertThat(response.getConversionResults()).hasSize(1);
    io.harness.spec.server.pipeline.v1.model.ConversionNodeSummary resultNode = response.getConversionResults().get(0);
    assertThat(resultNode.getPipelineMetrics()).isNotNull();
    assertThat(resultNode.getPipelineMetrics().getTotalInputSets()).isEqualTo(3);
    assertThat(resultNode.getPipelineMetrics().getConvertedInputSets()).isEqualTo(2);
    assertThat(resultNode.getPipelineMetrics().getFailedInputSets()).isEqualTo(1);
    assertThat(resultNode.getPipelineMetrics().getTotalTriggers()).isEqualTo(2);
    assertThat(resultNode.getPipelineMetrics().getConvertedTriggers()).isEqualTo(2);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testToResponseBodyWithConversionErrors() {
    ConversionErrorDetail error = ConversionErrorDetail.builder()
                                      .code("UNSUPPORTED_STEP")
                                      .message("Step type not supported")
                                      .severity(ErrorSeverity.WARNING)
                                      .entityIdentifier("myPipeline")
                                      .entityType(EntityType.PIPELINE)
                                      .build();

    ConversionNodeSummary node = ConversionNodeSummary.builder()
                                     .entityIdentifier("myPipeline")
                                     .entityType(EntityType.PIPELINE)
                                     .status(ConversionStatus.SUCCESS)
                                     .errors(Collections.singletonList(error))
                                     .build();

    ConversionJobEntity entity = ConversionJobEntity.builder()
                                     .uuid("job-1")
                                     .status(ConversionStatus.SUCCESS)
                                     .actionType(ConversionActionType.SINGLE)
                                     .entityType(EntityType.PIPELINE)
                                     .accountId(ACCOUNT_ID)
                                     .conversionResults(Collections.singletonList(node))
                                     .build();

    ConversionJobResponseBody response = ConversionJobApiMapper.toResponseBody(entity);

    assertThat(response.getConversionResults()).hasSize(1);
    io.harness.spec.server.pipeline.v1.model.ConversionNodeSummary resultNode = response.getConversionResults().get(0);
    assertThat(resultNode.getErrors()).hasSize(1);
    assertThat(resultNode.getErrors().get(0).getCode()).isEqualTo("UNSUPPORTED_STEP");
    assertThat(resultNode.getErrors().get(0).getSeverity())
        .isEqualTo(io.harness.spec.server.pipeline.v1.model.ConversionErrorDetail.SeverityEnum.WARNING);
  }
}
