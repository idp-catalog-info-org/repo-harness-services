/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.service;

import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.goconvert.EntityType;
import io.harness.pms.conversion.beans.ConversionActionType;
import io.harness.pms.conversion.beans.ConversionJobEntity;
import io.harness.pms.conversion.beans.ConversionJobMetricsDTO;
import io.harness.pms.conversion.beans.ConversionStatus;
import io.harness.repositories.conversion.ConversionChecksumRepository;
import io.harness.repositories.conversion.ConversionJobRepository;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

public class ConversionJobServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account123";
  private static final String ORG_ID = "org1";
  private static final String PROJECT_ID = "project1";
  private static final String JOB_UUID = "job-uuid-1";

  @Mock private ConversionJobRepository conversionJobRepository;
  @Mock private ConversionChecksumRepository conversionChecksumRepository;
  private ConversionJobServiceImpl conversionJobService;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    conversionJobService = new ConversionJobServiceImpl(conversionJobRepository, conversionChecksumRepository);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testCreateJob() {
    ConversionJobEntity jobEntity = buildJobEntity(ConversionStatus.QUEUED);
    when(conversionJobRepository.save(any(ConversionJobEntity.class))).thenReturn(jobEntity);

    ConversionJobEntity result = conversionJobService.createJob(jobEntity);

    assertThat(result).isNotNull();
    assertThat(result.getUuid()).isEqualTo(JOB_UUID);
    verify(conversionJobRepository).save(jobEntity);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetJobByUuid() {
    ConversionJobEntity jobEntity = buildJobEntity(ConversionStatus.QUEUED);
    when(conversionJobRepository.findById(JOB_UUID)).thenReturn(Optional.of(jobEntity));

    Optional<ConversionJobEntity> result = conversionJobService.getJobByUuid(JOB_UUID);

    assertThat(result).isPresent();
    assertThat(result.get().getUuid()).isEqualTo(JOB_UUID);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetJobByUuidNotFound() {
    when(conversionJobRepository.findById("nonexistent")).thenReturn(Optional.empty());

    Optional<ConversionJobEntity> result = conversionJobService.getJobByUuid("nonexistent");

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetJobByEntityScope() {
    ConversionJobEntity jobEntity = buildJobEntity(ConversionStatus.SUCCESS);
    when(conversionJobRepository.findOne(any(Criteria.class), any(Sort.class))).thenReturn(Optional.of(jobEntity));

    Optional<ConversionJobEntity> result =
        conversionJobService.getJobByEntityScope(ACCOUNT_ID, ORG_ID, PROJECT_ID, "myPipeline", EntityType.PIPELINE);

    assertThat(result).isPresent();
    verify(conversionJobRepository).findOne(any(Criteria.class), any(Sort.class));
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetJobByEntityScopeWithNullOrg() {
    when(conversionJobRepository.findOne(any(Criteria.class), any(Sort.class))).thenReturn(Optional.empty());

    Optional<ConversionJobEntity> result =
        conversionJobService.getJobByEntityScope(ACCOUNT_ID, null, null, "accountTemplate", EntityType.TEMPLATE);

    assertThat(result).isEmpty();
    verify(conversionJobRepository).findOne(any(Criteria.class), any(Sort.class));
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testUpdateJobStatusToInProgress() {
    ConversionJobMetricsDTO metrics = ConversionJobMetricsDTO.builder().totalEntities(1).build();
    ConversionJobEntity updatedEntity = buildJobEntity(ConversionStatus.IN_PROGRESS);
    when(conversionJobRepository.update(any(Criteria.class), any(Update.class))).thenReturn(updatedEntity);

    ConversionJobEntity result = conversionJobService.updateJobStatus(JOB_UUID, ConversionStatus.IN_PROGRESS, metrics);

    assertThat(result).isNotNull();
    verify(conversionJobRepository).update(any(Criteria.class), any(Update.class));
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testUpdateJobStatusToFinalStatusUnsetsMetadata() {
    ConversionJobMetricsDTO metrics = ConversionJobMetricsDTO.builder()
                                          .totalEntities(1)
                                          .processedEntities(1)
                                          .convertedEntities(1)
                                          .progressPercentage(100)
                                          .build();
    ConversionJobEntity updatedEntity = buildJobEntity(ConversionStatus.SUCCESS);
    when(conversionJobRepository.update(any(Criteria.class), any(Update.class))).thenReturn(updatedEntity);

    conversionJobService.updateJobStatus(JOB_UUID, ConversionStatus.SUCCESS, metrics);

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(conversionJobRepository).update(any(Criteria.class), updateCaptor.capture());
    String updateStr = updateCaptor.getValue().toString();
    assertThat(updateStr).contains("entityMetadata");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testRetryJob() {
    ConversionJobEntity retriedEntity = buildJobEntity(ConversionStatus.FAILED);
    when(conversionJobRepository.findAll(any(Criteria.class), any(Sort.class)))
        .thenReturn(java.util.Collections.emptyList());
    when(conversionJobRepository.findById(JOB_UUID)).thenReturn(Optional.of(retriedEntity));
    when(conversionJobRepository.update(any(Criteria.class), any(Update.class))).thenReturn(retriedEntity);

    ConversionJobEntity result = conversionJobService.retryJob(JOB_UUID);

    assertThat(result).isNotNull();
    verify(conversionJobRepository).update(any(Criteria.class), any(Update.class));
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetChildJobs() {
    ConversionJobEntity child1 = buildChildJobEntity("child-1", ConversionStatus.SUCCESS);
    ConversionJobEntity child2 = buildChildJobEntity("child-2", ConversionStatus.IN_PROGRESS);
    when(conversionJobRepository.findAll(any(Criteria.class), any(Sort.class)))
        .thenReturn(Arrays.asList(child1, child2));

    List<ConversionJobEntity> children = conversionJobService.getChildJobs(JOB_UUID);

    assertThat(children).hasSize(2);
    verify(conversionJobRepository).findAll(any(Criteria.class), any(Sort.class));
  }

  private ConversionJobEntity buildJobEntity(ConversionStatus status) {
    return ConversionJobEntity.builder()
        .uuid(JOB_UUID)
        .status(status)
        .accountId(ACCOUNT_ID)
        .orgId(ORG_ID)
        .projectId(PROJECT_ID)
        .actionType(ConversionActionType.SINGLE)
        .entityType(EntityType.PIPELINE)
        .entityIdentifier("myPipeline")
        .build();
  }

  private ConversionJobEntity buildChildJobEntity(String uuid, ConversionStatus status) {
    return ConversionJobEntity.builder()
        .uuid(uuid)
        .status(status)
        .accountId(ACCOUNT_ID)
        .orgId(ORG_ID)
        .projectId(PROJECT_ID)
        .actionType(ConversionActionType.SINGLE)
        .entityType(EntityType.PIPELINE)
        .parentJobId(JOB_UUID)
        .build();
  }
}
