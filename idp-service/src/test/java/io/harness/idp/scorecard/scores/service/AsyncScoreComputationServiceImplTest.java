/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.service;

import static io.harness.rule.OwnerRule.AGNIVA;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.UnexpectedException;
import io.harness.idp.events.producers.IdpEntityCrudStreamProducer;
import io.harness.idp.scorecard.scores.entity.AsyncScoreComputationEntity;
import io.harness.idp.scorecard.scores.repositories.AsyncAsyncScoreComputationRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateInfo;

import com.google.protobuf.StringValue;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class AsyncScoreComputationServiceImplTest extends CategoryTest {
  @Mock AsyncAsyncScoreComputationRepository asyncScoreComputationRepository;
  @Mock IdpEntityCrudStreamProducer idpEntityCrudStreamProducer;
  @Mock ScoreComputerService scoreComputerService;
  @InjectMocks AsyncScoreComputationServiceImpl asyncScoreComputationServiceImpl;

  private static final String harnessAccount = "agniva@harness";
  private static final String scorecardIdentifier = "agnivascorecard";
  private static final String entityIdentifier = "agnivaEntity";
  private static final String USER_NAME = "John Doe";
  private static final String USER_UUID = "user-1234";
  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testgetRecalibrateInfo() {
    EmbeddedUser createdBy = EmbeddedUser.builder().name(USER_NAME).email("johndoe@example.com").build();
    AsyncScoreComputationEntity entity = AsyncScoreComputationEntity.builder()
                                             .id("entityId123")
                                             .accountIdentifier("accountId123")
                                             .entityIdentifier("entityId456")
                                             .scorecardIdentifier("scorecardId789")
                                             .startTime(System.currentTimeMillis())
                                             .createdBy(createdBy)
                                             .build();

    Optional<AsyncScoreComputationEntity> scoreComputationEntityOpt = Optional.of(entity);
    when(asyncScoreComputationRepository.findByAccountIdentifierAndScorecardIdentifierAndEntityIdentifier(
             harnessAccount, scorecardIdentifier, entityIdentifier))
        .thenReturn(scoreComputationEntityOpt);
    ScorecardRecalibrateInfo result =
        asyncScoreComputationServiceImpl.getRecalibrateInfo(harnessAccount, scorecardIdentifier, entityIdentifier);
    assertEquals(USER_NAME, result.getStartedBy().getName());
    assertEquals("johndoe@example.com", result.getStartedBy().getEmail());
    when(asyncScoreComputationRepository.findByAccountIdentifierAndScorecardIdentifierAndEntityIdentifier(
             harnessAccount, scorecardIdentifier, entityIdentifier))
        .thenReturn(Optional.empty());
    result = asyncScoreComputationServiceImpl.getRecalibrateInfo(harnessAccount, scorecardIdentifier, entityIdentifier);
    assertNull(result);
  }
  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testLogScoreComputationRequestAndPublishEvent() {
    EmbeddedUser embeddedUser = EmbeddedUser.builder()
                                    .uuid(USER_UUID)
                                    .name(USER_NAME)
                                    .email("johndoe@example.com")
                                    .externalUserId("external-5678")
                                    .build();
    AsyncScoreComputationEntity savedEntity = AsyncScoreComputationEntity.builder()
                                                  .id("entityId123")
                                                  .startTime(System.currentTimeMillis())
                                                  .createdBy(embeddedUser)
                                                  .accountIdentifier(harnessAccount)
                                                  .scorecardIdentifier(scorecardIdentifier)
                                                  .entityIdentifier(entityIdentifier)
                                                  .build();
    when(asyncScoreComputationRepository.save(any(AsyncScoreComputationEntity.class))).thenReturn(savedEntity);
    when(idpEntityCrudStreamProducer.publishAsyncScoreComputationChangeEventToRedis(
             anyString(), anyString(), anyString()))
        .thenReturn(true);
    ScorecardRecalibrateInfo result = asyncScoreComputationServiceImpl.logScoreComputationRequestAndPublishEvent(
        harnessAccount, scorecardIdentifier, entityIdentifier);
    assertNotNull(result);
    assertEquals(USER_NAME, result.getStartedBy().getName());
    assertEquals(USER_UUID, result.getStartedBy().getUuid());
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testLogScoreComputationRequestAndPublishEventwithFalseProducerResult() {
    EmbeddedUser embeddedUser = EmbeddedUser.builder()
                                    .uuid(USER_UUID)
                                    .name(USER_NAME)
                                    .email("johndoe@example.com")
                                    .externalUserId("external-5678")
                                    .build();
    AsyncScoreComputationEntity savedEntity = AsyncScoreComputationEntity.builder()
                                                  .id("entityId123")
                                                  .startTime(System.currentTimeMillis())
                                                  .createdBy(embeddedUser)
                                                  .accountIdentifier(harnessAccount)
                                                  .scorecardIdentifier(scorecardIdentifier)
                                                  .entityIdentifier(entityIdentifier)
                                                  .build();
    when(asyncScoreComputationRepository.save(any(AsyncScoreComputationEntity.class))).thenReturn(savedEntity);
    when(idpEntityCrudStreamProducer.publishAsyncScoreComputationChangeEventToRedis(
             anyString(), anyString(), anyString()))
        .thenReturn(false);
    try {
      asyncScoreComputationServiceImpl.logScoreComputationRequestAndPublishEvent(
          harnessAccount, scorecardIdentifier, entityIdentifier);
    } catch (UnexpectedException e) {
      assertEquals("Error in producing event for async score computation.", e.getMessage());
    }
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testDeleteScoreComputationRequest() {
    asyncScoreComputationServiceImpl.deleteScoreComputationRequest(
        harnessAccount, scorecardIdentifier, entityIdentifier);
    verify(asyncScoreComputationRepository)
        .deleteByAccountIdentifierAndScorecardIdentifierAndEntityIdentifier(
            harnessAccount, scorecardIdentifier, entityIdentifier);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testTriggerScoreComputation() {
    EntityChangeDTO entityChangeDTO =
        EntityChangeDTO
            .newBuilder()

            .setIdentifier(StringValue.newBuilder().setValue("entity123").build())
            .setAccountIdentifier(StringValue.newBuilder().setValue("account456").build())
            .setOrgIdentifier(StringValue.newBuilder().setValue("org789").build())
            .setProjectIdentifier(StringValue.newBuilder().setValue("proj1011").build())
            .putAllMetadata(Map.of("entityIdentifier", "value1", "scorecardIdentifier", "value2"))
            .setUniqueId(StringValue.newBuilder().setValue("unique-id-123").build())
            .build();
    doNothing().when(scoreComputerService).computeScores(any(), any(), any());
    doNothing()
        .when(asyncScoreComputationRepository)
        .deleteByAccountIdentifierAndScorecardIdentifierAndEntityIdentifier(any(), any(), any());
    asyncScoreComputationServiceImpl.triggerScoreComputation(entityChangeDTO);
    verify(asyncScoreComputationRepository)
        .deleteByAccountIdentifierAndScorecardIdentifierAndEntityIdentifier(any(), any(), any());
  }
}
