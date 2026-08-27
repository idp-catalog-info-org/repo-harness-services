/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.annotations;

import static io.harness.pms.annotations.AnnotationConstants.MODE_REPLACE;
import static io.harness.rule.OwnerRule.DHIRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.annotations.AnnotationStyle;
import io.harness.category.element.UnitTests;
import io.harness.pms.annotations.PipelineAnnotationEntity;
import io.harness.repositories.annotations.PipelineAnnotationRepositoryCustomImpl;
import io.harness.rule.Owner;

import com.mongodb.client.result.DeleteResult;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.CI)
public class PipelineAnnotationRepositoryCustomImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "acc123";
  private static final String ORG_ID = "org123";
  private static final String PROJECT_ID = "proj123";
  private static final String PIPELINE_ID = "pipe123";
  private static final String PLAN_EXECUTION_ID = "plan456";
  private static final String CONTEXT_ID = "test-context";

  @Mock private MongoTemplate mongoTemplate;
  @Mock private DeleteResult deleteResult;

  private PipelineAnnotationRepositoryCustomImpl repository;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    repository = new PipelineAnnotationRepositoryCustomImpl(mongoTemplate);
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testUpsertAnnotation_Insert_Success() {
    // Arrange
    PipelineAnnotationEntity entity = createEntity("preview content");
    PipelineAnnotationEntity savedEntity = createEntity("preview content");

    when(mongoTemplate.findAndModify(
             any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PipelineAnnotationEntity.class)))
        .thenReturn(savedEntity);

    // Act
    PipelineAnnotationEntity result = repository.upsertAnnotation(entity, MODE_REPLACE);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getSummary()).isEqualTo("preview content");

    // Verify MongoDB operations
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    ArgumentCaptor<FindAndModifyOptions> optionsCaptor = ArgumentCaptor.forClass(FindAndModifyOptions.class);

    verify(mongoTemplate)
        .findAndModify(
            queryCaptor.capture(), updateCaptor.capture(), optionsCaptor.capture(), eq(PipelineAnnotationEntity.class));

    // Verify options
    FindAndModifyOptions options = optionsCaptor.getValue();
    assertThat(options.isReturnNew()).isTrue();
    assertThat(options.isUpsert()).isTrue();
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testDeleteAnnotation_Success() {
    // Arrange
    when(deleteResult.getDeletedCount()).thenReturn(1L);
    when(mongoTemplate.remove(any(Query.class), eq(PipelineAnnotationEntity.class))).thenReturn(deleteResult);

    // Act
    boolean result = repository.deleteAnnotation(PLAN_EXECUTION_ID, CONTEXT_ID);

    // Assert
    assertThat(result).isTrue();

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).remove(queryCaptor.capture(), eq(PipelineAnnotationEntity.class));
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testFindAllByPlanExecutionId_MultipleResults() {
    // Arrange
    PipelineAnnotationEntity entity1 = createEntity("preview1");
    entity1.setContextId("ctx1");
    PipelineAnnotationEntity entity2 = createEntity("preview2");
    entity2.setContextId("ctx2");

    when(mongoTemplate.find(any(Query.class), eq(PipelineAnnotationEntity.class)))
        .thenReturn(Arrays.asList(entity1, entity2));

    // Act
    List<PipelineAnnotationEntity> results = repository.findAllByPlanExecutionId(PLAN_EXECUTION_ID);

    // Assert
    assertThat(results).hasSize(2);
    assertThat(results.get(0).getContextId()).isEqualTo("ctx1");
    assertThat(results.get(1).getContextId()).isEqualTo("ctx2");

    verify(mongoTemplate).find(any(Query.class), eq(PipelineAnnotationEntity.class));
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testFindByPlanExecutionIdAndContextId_Found() {
    // Arrange
    PipelineAnnotationEntity entity = createEntity("preview");

    when(mongoTemplate.findOne(any(Query.class), eq(PipelineAnnotationEntity.class))).thenReturn(entity);

    // Act
    PipelineAnnotationEntity result = repository.findByPlanExecutionIdAndContextId(PLAN_EXECUTION_ID, CONTEXT_ID);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getContextId()).isEqualTo(CONTEXT_ID);
    assertThat(result.getSummary()).isEqualTo("preview");

    verify(mongoTemplate).findOne(any(Query.class), eq(PipelineAnnotationEntity.class));
  }

  // Helper methods
  private PipelineAnnotationEntity createEntity(String summary) {
    return PipelineAnnotationEntity.builder()
        .accountId(ACCOUNT_ID)
        .orgId(ORG_ID)
        .projectId(PROJECT_ID)
        .pipelineId(PIPELINE_ID)
        .planExecutionId(PLAN_EXECUTION_ID)
        .contextId(CONTEXT_ID)
        .summary(summary)
        .style(AnnotationStyle.INFO.getDisplayName())
        .priority(5)
        .lastUpdatedAt(System.currentTimeMillis())
        .build();
  }
}
