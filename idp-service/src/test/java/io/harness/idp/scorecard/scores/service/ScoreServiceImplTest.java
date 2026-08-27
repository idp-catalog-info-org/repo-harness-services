/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.service;

import static io.harness.rule.OwnerRule.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.UnexpectedException;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.catalog.cache.CatalogScopeTopologyCache;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.scorecards.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.service.ApplicabilityEngine;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.idp.scorecard.scores.repositories.ScoreEntityByScorecardIdentifier;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CheckStatus;
import io.harness.spec.server.idp.v1.model.EntityScores;
import io.harness.spec.server.idp.v1.model.ScorecardChecksDetails;
import io.harness.spec.server.idp.v1.model.ScorecardDetails;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsResponse;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.idp.v1.model.ScorecardGraphSummaryInfo;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateInfo;
import io.harness.spec.server.idp.v1.model.ScorecardScore;
import io.harness.spec.server.idp.v1.model.ScorecardSummaryInfo;
import io.harness.spec.server.idp.v1.model.User;
import io.harness.springdata.TransactionHelper;

import com.google.common.io.Resources;
import com.mongodb.client.result.UpdateResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ScoreServiceImplTest extends CategoryTest {
  @Mock ScorecardService scorecardService;
  @Mock ScoreRepository scoreRepository;
  @Mock ScoreComputerService scoreComputerService;
  @Mock TransactionHelper transactionHelper;
  @Mock AsyncScoreComputationService asyncScoreComputationService;
  @Mock NamespaceService namespaceService;
  @Mock IdpCommonService idpCommonService;
  @Mock CatalogScopeTopologyCache scopeTopologyCache;
  @Mock CatalogServiceHelper catalogServiceHelper;
  @Mock ApplicabilityEngine applicabilityEngine;
  @InjectMocks ScoreServiceImpl scoreServiceimpl;
  private final java.util.concurrent.ExecutorService scorecardSummaryExecutor =
      java.util.concurrent.Executors.newFixedThreadPool(2);
  List<ScorecardAndChecks> scorecardAndChecks;
  Set<BackstageCatalogEntity> mockEntities = new HashSet<>();
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    java.lang.reflect.Field executorField = ScoreServiceImpl.class.getDeclaredField("scorecardSummaryExecutor");
    executorField.setAccessible(true);
    executorField.set(scoreServiceimpl, scorecardSummaryExecutor);
    getScoreCardandChecks();
    getbackstagecatalogentity();
  }

  private static final String accountidentifier = "accountidentifier";
  private static final String entityIdentifier = "kind/namespace/name";
  private static final String entityIdentifier2 = "Component/Domain/Group";
  private static final String modifiedEntityIdentifier = "uniqueId";
  private static final String SCORECARD_MIGRATIONS_FOLDER_PATH = "migrations/scorecard/";

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testpopulateData() {
    String checkEntities = loadResourceFileAsString(SCORECARD_MIGRATIONS_FOLDER_PATH + "checks.json");
    String datapointEntities = loadResourceFileAsString(SCORECARD_MIGRATIONS_FOLDER_PATH + "dataPoints.json");
    String datasourceEntities = loadResourceFileAsString(SCORECARD_MIGRATIONS_FOLDER_PATH + "dataSources.json");
    String datasourceLocationEntities =
        loadResourceFileAsString(SCORECARD_MIGRATIONS_FOLDER_PATH + "datasourceLocations.json");
    when(transactionHelper.performTransaction(any())).thenReturn(null);
    scoreServiceimpl.populateData(checkEntities, datapointEntities, datasourceEntities, datasourceLocationEntities);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testgetScoresSummaryForAnEntity_onlyOneComputedOutOfTwo() {
    when(scorecardService.getAllScorecardAndChecks(any(), any())).thenReturn(scorecardAndChecks);
    Mockito
        .<Set<? extends BackstageCatalogEntity>>when(
            scoreComputerService.getBackstageEntitiesForScorecardsAndEntityIdentifiers(any(), any(), any()))
        .thenReturn(mockEntities);
    ScoreEntity scoreEntity1 = ScoreEntity.builder().id("scorecard1").score(85).build();
    List<ScoreEntityByScorecardIdentifier> scoreentitybyscorecardidentifierlist = new ArrayList<>();
    ScoreEntityByScorecardIdentifier scoreentitybyscorecardidentifier1 =
        ScoreEntityByScorecardIdentifier.builder().scorecardIdentifier("scorecard1").scoreEntity(scoreEntity1).build();
    scoreentitybyscorecardidentifierlist.add(scoreentitybyscorecardidentifier1);
    AggregationResults<ScoreEntityByScorecardIdentifier> mockResults = mock(AggregationResults.class);
    when(scoreRepository.getAllLatestScoresByScorecardsForAnEntity(accountidentifier, entityIdentifier, false))
        .thenReturn(mockResults);
    when(mockResults.getMappedResults()).thenReturn(scoreentitybyscorecardidentifierlist);
    when(scoreComputerService.isFilterMatchingWithAnEntity(any(), any())).thenReturn(true);
    User startedBy = new User();
    startedBy.setUuid("user-id");
    startedBy.setName("John Doe");
    ScorecardRecalibrateInfo recalibrateInfo = new ScorecardRecalibrateInfo().startedBy(startedBy);
    when(asyncScoreComputationService.getRecalibrateInfo(any(), any(), any())).thenReturn(recalibrateInfo);
    when(idpCommonService.idpV2Enabled(accountidentifier)).thenReturn(false);
    List<ScorecardSummaryInfo> result =
        scoreServiceimpl.getScoresSummaryForAnEntity(accountidentifier, entityIdentifier);
    assertEquals(1, result.size());
    assertEquals("Scorecard 1", result.get(0).getScorecardName());
    assertEquals((int) 85, (int) result.get(0).getScore());
    assertEquals("Description for Scorecard 1", result.get(0).getDescription());
    assertEquals("scorecard1", result.get(0).getScorecardIdentifier());
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testgetScoresSummaryForAnEntity_twoComputedOutOfTwo() {
    when(scorecardService.getAllScorecardAndChecks(any(), any())).thenReturn(scorecardAndChecks);
    Mockito
        .<Set<? extends BackstageCatalogEntity>>when(
            scoreComputerService.getBackstageEntitiesForScorecardsAndEntityIdentifiers(any(), any(), any()))
        .thenReturn(mockEntities);
    List<ScoreEntityByScorecardIdentifier> scoreentitybyscorecardidentifierlist = new ArrayList<>();
    ScoreEntity scoreEntity1 = ScoreEntity.builder().id("scorecard1").score(85).build();
    ScoreEntityByScorecardIdentifier scoreentitybyscorecardidentifier1 =
        ScoreEntityByScorecardIdentifier.builder().scorecardIdentifier("scorecard1").scoreEntity(scoreEntity1).build();
    scoreentitybyscorecardidentifierlist.add(scoreentitybyscorecardidentifier1);
    ScoreEntity scoreEntity2 = ScoreEntity.builder().id("scorecard2").score(86).build();
    ScoreEntityByScorecardIdentifier scoreentitybyscorecardidentifier2 =
        ScoreEntityByScorecardIdentifier.builder().scorecardIdentifier("scorecard2").scoreEntity(scoreEntity2).build();
    scoreentitybyscorecardidentifierlist.add(scoreentitybyscorecardidentifier2);
    AggregationResults<ScoreEntityByScorecardIdentifier> mockResults = mock(AggregationResults.class);
    when(scoreRepository.getAllLatestScoresByScorecardsForAnEntity(accountidentifier, entityIdentifier, false))
        .thenReturn(mockResults);
    when(mockResults.getMappedResults()).thenReturn(scoreentitybyscorecardidentifierlist);
    when(scoreComputerService.isFilterMatchingWithAnEntity(any(), any())).thenReturn(true);
    User startedBy = new User();
    startedBy.setUuid("user-id");
    startedBy.setName("John Doe");
    ScorecardRecalibrateInfo recalibrateInfo = new ScorecardRecalibrateInfo().startedBy(startedBy);
    when(asyncScoreComputationService.getRecalibrateInfo(any(), any(), any())).thenReturn(recalibrateInfo);
    when(idpCommonService.idpV2Enabled(accountidentifier)).thenReturn(false);
    List<ScorecardSummaryInfo> result =
        scoreServiceimpl.getScoresSummaryForAnEntity(accountidentifier, entityIdentifier);
    assertEquals(2, result.size());
    result.sort(Comparator.comparingInt(ScorecardSummaryInfo::getScore).reversed());
    assertEquals("Scorecard 1", result.get(1).getScorecardName());
    assertEquals((int) 85, (int) result.get(1).getScore());
    assertEquals("Description for Scorecard 1", result.get(1).getDescription());
    assertEquals("scorecard1", result.get(1).getScorecardIdentifier());
    assertEquals("Scorecard 2", result.get(0).getScorecardName());
    assertEquals((int) 86, (int) result.get(0).getScore());
    assertEquals("Description for Scorecard 2", result.get(0).getDescription());
    assertEquals("scorecard2", result.get(0).getScorecardIdentifier());
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testgetScoresGraphSummaryForAnEntityAndScorecard() {
    String accountIdentifier = "account1";
    String entityIdentifier = "entity1";
    String scorecardIdentifier = "scorecard1";
    ScoreEntity scoreEntity1 = ScoreEntity.builder().id("scorecard1").score(85).build();
    ScoreEntity scoreEntity2 = ScoreEntity.builder().id("scorecard2").score(86).build();
    List<ScoreEntity> scoreEntities = Arrays.asList(scoreEntity1, scoreEntity2);
    when(scoreRepository.findAllByAccountIdentifierAndEntityIdentifierIgnoreCaseAndScorecardIdentifier(
             anyString(), anyString(), anyString()))
        .thenReturn(scoreEntities);
    when(idpCommonService.idpV2Enabled(accountidentifier)).thenReturn(false);
    List<ScorecardGraphSummaryInfo> result = scoreServiceimpl.getScoresGraphSummaryForAnEntityAndScorecard(
        accountIdentifier, entityIdentifier, scorecardIdentifier);
    assertEquals((long) 85, (long) result.get(0).getScore());
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testgetScorecardScoreOverviewForAnEntity() {
    when(scorecardService.getAllScorecardAndChecks(any(), any())).thenReturn(scorecardAndChecks);
    Mockito
        .<Set<? extends BackstageCatalogEntity>>when(
            scoreComputerService.getBackstageEntitiesForScorecardsAndEntityIdentifiers(any(), any(), any()))
        .thenReturn(mockEntities);
    ScoreEntity scoreEntity1 = ScoreEntity.builder().id("scorecard1").score(85).build();
    List<ScoreEntityByScorecardIdentifier> scoreentitybyscorecardidentifierlist = new ArrayList<>();
    ScoreEntityByScorecardIdentifier scoreentitybyscorecardidentifier1 =
        ScoreEntityByScorecardIdentifier.builder().scorecardIdentifier("scorecard1").scoreEntity(scoreEntity1).build();
    scoreentitybyscorecardidentifierlist.add(scoreentitybyscorecardidentifier1);
    AggregationResults<ScoreEntityByScorecardIdentifier> mockResults = mock(AggregationResults.class);
    when(scoreRepository.getAllLatestScoresByScorecardsForAnEntity(accountidentifier, entityIdentifier, false))
        .thenReturn(mockResults);
    when(mockResults.getMappedResults()).thenReturn(scoreentitybyscorecardidentifierlist);
    when(scoreComputerService.isFilterMatchingWithAnEntity(any(), any())).thenReturn(true);
    when(idpCommonService.idpV2Enabled(accountidentifier)).thenReturn(false);
    List<ScorecardScore> result =
        scoreServiceimpl.getScorecardScoreOverviewForAnEntity(accountidentifier, entityIdentifier);
    assertEquals(result.size(), 1);
    assertEquals(result.get(0).getDescription(), "Description for Scorecard 1");
    assertEquals(result.get(0).getScorecardName(), "Scorecard 1");
  }

  @Test(expected = UnsupportedOperationException.class)
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetScorecardScoreOverviewForAnEntity_NoMatchingScorecards() {
    when(idpCommonService.idpV2Enabled(accountidentifier)).thenReturn(false);
    when(scorecardService.getAllScorecardAndChecks(accountidentifier, null)).thenReturn(scorecardAndChecks);
    Mockito
        .<Set<? extends BackstageCatalogEntity>>when(
            scoreComputerService.getBackstageEntitiesForScorecardsAndEntityIdentifiers(
                accountidentifier, scorecardAndChecks, Collections.singletonList(entityIdentifier)))
        .thenReturn(mockEntities);
    List<ScoreEntityByScorecardIdentifier> emptyList = new ArrayList<>();
    AggregationResults<ScoreEntityByScorecardIdentifier> mockResults = mock(AggregationResults.class);
    when(scoreRepository.getAllLatestScoresByScorecardsForAnEntity(accountidentifier, entityIdentifier, false))
        .thenReturn(mockResults);
    when(mockResults.getMappedResults()).thenReturn(emptyList);
    when(scoreComputerService.isFilterMatchingWithAnEntity(any(), any())).thenReturn(false);
    scoreServiceimpl.getScorecardScoreOverviewForAnEntity(accountidentifier, entityIdentifier);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testgetScorecardRecalibratedScoreInfoForAnEntityAndScorecard() {
    ScorecardDetails scorecardDetails = new ScorecardDetails()
                                            .name("Sample Scorecard")
                                            .identifier("scorecard123")
                                            .description("This is a sample scorecard description")
                                            .weightageStrategy(ScorecardDetails.WeightageStrategyEnum.CUSTOM)
                                            .published(true)
                                            .onDemand(false)
                                            .checksMissing(List.of("Check1", "Check2"))
                                            .components(5);
    ScorecardChecksDetails check1 = new ScorecardChecksDetails();
    check1.setName("Check 1");
    check1.setDescription("Description for Check 1");
    ScorecardChecksDetails check2 = new ScorecardChecksDetails();
    check2.setName("Check 2");
    check2.setDescription("Description for Check 2");
    List<ScorecardChecksDetails> checks = new ArrayList<>();
    checks.add(check1);
    checks.add(check2);
    ScorecardDetailsResponse response = new ScorecardDetailsResponse();
    response.setChecks(checks);
    response.setScorecard(scorecardDetails);
    when(scorecardService.getScorecardDetails(any(), any())).thenReturn(response);
    doNothing().when(scoreComputerService).computeScores(anyString(), anyList(), anyList());
    ScoreEntity scoreEntity1 = ScoreEntity.builder().id("scorecard1").score(85).build();
    when(scoreRepository.getLatestComputedScoreForEntityAndScorecard(any(), any(), any(), anyBoolean()))
        .thenReturn(scoreEntity1);
    when(idpCommonService.idpV2Enabled(accountidentifier)).thenReturn(false);
    ScorecardSummaryInfo result =
        scoreServiceimpl.getScorecardRecalibratedScoreInfoForAnEntityAndScorecard("a", "b", "c");
    assertEquals("Sample Scorecard", result.getScorecardName());
    assertEquals("This is a sample scorecard description", result.getDescription());
    assertEquals("c", result.getScorecardIdentifier());
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testgetEntityScores_onlyOneScorecardComputedOutOfTwo() {
    Mockito
        .<Set<? extends BackstageCatalogEntity>>when(
            scoreComputerService.getBackstageEntitiesForScorecardsAndEntityIdentifiers(any(), any(), any()))
        .thenReturn(mockEntities);
    when(scoreComputerService.getAllEntities(any(), any(), any())).thenReturn(mockEntities);
    when(scorecardService.getAllScorecardAndChecks(any(), any())).thenReturn(scorecardAndChecks);
    ScoreEntity scoreEntity1 = ScoreEntity.builder().id("scorecard1").score(85).build();
    List<ScoreEntityByScorecardIdentifier> scoreentitybyscorecardidentifierlist = new ArrayList<>();
    ScoreEntityByScorecardIdentifier scoreentitybyscorecardidentifier1 =
        ScoreEntityByScorecardIdentifier.builder().scorecardIdentifier("scorecard1").scoreEntity(scoreEntity1).build();
    scoreentitybyscorecardidentifierlist.add(scoreentitybyscorecardidentifier1);
    AggregationResults<ScoreEntityByScorecardIdentifier> mockResults = mock(AggregationResults.class);
    when(scoreRepository.getAllLatestScoresByScorecardsForAnEntity(any(), any(), anyBoolean())).thenReturn(mockResults);
    when(mockResults.getMappedResults()).thenReturn(scoreentitybyscorecardidentifierlist);
    when(scoreComputerService.isFilterMatchingWithAnEntity(any(), any())).thenReturn(true);
    when(idpCommonService.idpV2Enabled(accountidentifier)).thenReturn(false);
    String kind = "SampleKind";
    String type = "SampleType";
    List<String> owners = Arrays.asList("owner1", "owner2");
    List<String> tags = Arrays.asList("tag1", "tag2");
    List<String> lifecycle = Arrays.asList("lifecycle1", "lifecycle2");
    ScorecardFilter scorecardFilter = new ScorecardFilter();
    scorecardFilter.setKind(kind);
    scorecardFilter.setType(type);
    scorecardFilter.setOwners(owners);
    scorecardFilter.setTags(tags);
    scorecardFilter.setLifecycle(lifecycle);
    List<EntityScores> entityScoresList = scoreServiceimpl.getEntityScores(accountidentifier, scorecardFilter);
    assertEquals("API", entityScoresList.get(0).getKind());
    assertEquals(1, entityScoresList.get(0).getScores().size());
    assertEquals("Description for Scorecard 1", entityScoresList.get(0).getScores().get(0).getDescription());
    assertEquals("Scorecard 1", entityScoresList.get(0).getScores().get(0).getScorecardName());
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testgetEntityScores_twoScorecardComputedOutOfTwo() {
    Mockito
        .<Set<? extends BackstageCatalogEntity>>when(
            scoreComputerService.getBackstageEntitiesForScorecardsAndEntityIdentifiers(any(), any(), any()))
        .thenReturn(mockEntities);
    when(scoreComputerService.getAllEntities(any(), any(), any())).thenReturn(mockEntities);
    when(scorecardService.getAllScorecardAndChecks(any(), any())).thenReturn(scorecardAndChecks);
    ScoreEntity scoreEntity1 = ScoreEntity.builder().id("scorecard1").score(85).build();
    List<ScoreEntityByScorecardIdentifier> scoreentitybyscorecardidentifierlist = new ArrayList<>();
    ScoreEntityByScorecardIdentifier scoreentitybyscorecardidentifier1 =
        ScoreEntityByScorecardIdentifier.builder().scorecardIdentifier("scorecard1").scoreEntity(scoreEntity1).build();
    scoreentitybyscorecardidentifierlist.add(scoreentitybyscorecardidentifier1);
    ScoreEntity scoreEntity2 = ScoreEntity.builder().id("scorecard2").score(86).build();
    ScoreEntityByScorecardIdentifier scoreentitybyscorecardidentifier2 =
        ScoreEntityByScorecardIdentifier.builder().scorecardIdentifier("scorecard2").scoreEntity(scoreEntity2).build();
    scoreentitybyscorecardidentifierlist.add(scoreentitybyscorecardidentifier2);
    AggregationResults<ScoreEntityByScorecardIdentifier> mockResults = mock(AggregationResults.class);
    when(scoreRepository.getAllLatestScoresByScorecardsForAnEntity(any(), any(), anyBoolean())).thenReturn(mockResults);
    when(mockResults.getMappedResults()).thenReturn(scoreentitybyscorecardidentifierlist);
    when(scoreComputerService.isFilterMatchingWithAnEntity(any(), any())).thenReturn(true);
    when(idpCommonService.idpV2Enabled(accountidentifier)).thenReturn(false);
    String kind = "SampleKind";
    String type = "SampleType";
    List<String> owners = Arrays.asList("owner1", "owner2");
    List<String> tags = Arrays.asList("tag1", "tag2");
    List<String> lifecycle = Arrays.asList("lifecycle1", "lifecycle2");
    ScorecardFilter scorecardFilter = new ScorecardFilter();
    scorecardFilter.setKind(kind);
    scorecardFilter.setType(type);
    scorecardFilter.setOwners(owners);
    scorecardFilter.setTags(tags);
    scorecardFilter.setLifecycle(lifecycle);
    List<EntityScores> entityScoresList = scoreServiceimpl.getEntityScores(accountidentifier, scorecardFilter);
    assertEquals("API", entityScoresList.get(0).getKind());
    assertEquals(2, entityScoresList.get(0).getScores().size());
    entityScoresList.get(0).getScores().sort(Comparator.comparingInt(ScorecardScore::getScore).reversed());
    assertEquals("Description for Scorecard 1", entityScoresList.get(0).getScores().get(1).getDescription());
    assertEquals("Description for Scorecard 2", entityScoresList.get(0).getScores().get(0).getDescription());
    assertEquals("Scorecard 1", entityScoresList.get(0).getScores().get(1).getScorecardName());
    assertEquals("Scorecard 2", entityScoresList.get(0).getScores().get(0).getScorecardName());
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testmigrateScoresWithCheckIdentifier() {
    when(namespaceService.getAccountIds()).thenReturn(Collections.singletonList("account1"));
    when(scorecardService.getAllScorecardAndChecks(anyString(), any())).thenReturn(scorecardAndChecks);
    List<CheckStatus> listcheckstatus = new ArrayList<>();
    CheckStatus checkStatus = new CheckStatus();
    checkStatus.setReason("Validation passed successfully");
    checkStatus.setIdentifier("check1");
    checkStatus.setName("1");
    checkStatus.setCustom(true);
    checkStatus.setStatus(CheckStatus.StatusEnum.PASS);
    checkStatus.setWeight(10);
    listcheckstatus.add(checkStatus);
    ScoreEntity scoreEntity1 = ScoreEntity.builder().id("scorecard1").score(85).checkStatus(listcheckstatus).build();
    List<ScoreEntity> scoreEntities = Arrays.asList(scoreEntity1);
    when(scoreRepository.findAllByAccountIdentifierAndScorecardIdentifier(anyString(), anyString()))
        .thenReturn(scoreEntities);
    UpdateResult updateResult = mock(UpdateResult.class);
    when(scoreRepository.updateCheckIdentifier(any(), any())).thenReturn(updateResult);
    when(updateResult.getModifiedCount()).thenReturn(1L);
    scoreServiceimpl.migrateScoresWithCheckIdentifier();
    verify(scoreRepository, times(2)).updateCheckIdentifier(any(), any());
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testmigrateEntityIdentifier() {
    String OLD_ENTITY_IDENTIFIER = "old-entity";
    String NEW_ENTITY_IDENTIFIER = "new-entity";
    String accountIdentifier = "account1";
    Map<String, String> entityIdentifiersMap = Map.of(OLD_ENTITY_IDENTIFIER, NEW_ENTITY_IDENTIFIER);
    List<String> entityIdentifiers = new ArrayList<>(List.of(OLD_ENTITY_IDENTIFIER));
    when(scoreRepository.findUniqueEntityIdentifiers(accountIdentifier)).thenReturn(entityIdentifiers);
    assertTrue("Entity identifier map should contain OLD_ENTITY_IDENTIFIER",
        entityIdentifiersMap.containsKey(OLD_ENTITY_IDENTIFIER));
    UpdateResult updateResult = mock(UpdateResult.class);
    when(updateResult.getModifiedCount()).thenReturn(1L);
    when(scoreRepository.updateEntityIdentifier(accountIdentifier, OLD_ENTITY_IDENTIFIER, NEW_ENTITY_IDENTIFIER))
        .thenReturn(updateResult);
    scoreServiceimpl.migrateEntityIdentifier(entityIdentifiersMap, accountIdentifier);
    verify(scoreRepository, times(1)).findUniqueEntityIdentifiers(accountIdentifier);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testModifyEntityIdentifier() {
    String accountIdentifier = "account1";
    List<String> entityIdentifiers = new ArrayList<>(List.of(entityIdentifier));
    when(scoreRepository.findUniqueEntityIdentifiers(accountIdentifier)).thenReturn(entityIdentifiers);
    UpdateResult updateResult = mock(UpdateResult.class);
    when(updateResult.getModifiedCount()).thenReturn(1L);
    when(scoreRepository.updateEntityIdentifier(accountIdentifier, entityIdentifier, modifiedEntityIdentifier))
        .thenReturn(updateResult);
    scoreServiceimpl.modifyEntityIdentifier(accountIdentifier);
    verify(scoreRepository, times(1)).findUniqueEntityIdentifiers(accountIdentifier);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testModifyEntityIdentifierwithBackstageKinds() {
    String accountIdentifier = "account1";
    List<String> entityIdentifiers = new ArrayList<>(List.of(entityIdentifier2));
    when(scoreRepository.findUniqueEntityIdentifiers(accountIdentifier)).thenReturn(entityIdentifiers);
    UpdateResult updateResult = mock(UpdateResult.class);
    when(updateResult.getModifiedCount()).thenReturn(1L);
    when(scoreRepository.updateEntityIdentifier(accountIdentifier, entityIdentifier2, "Domain/Component/Group"))
        .thenReturn(updateResult);
    scoreServiceimpl.modifyEntityIdentifier(accountIdentifier);
    verify(scoreRepository).updateEntityIdentifier(anyString(), anyString(), anyString());
  }

  @Test(expected = UnsupportedOperationException.class)
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetScoresSummaryForAnEntityV2_noScoresPresent() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setScopes(List.of("account.*"));
    filter.setKind("component");

    ScorecardEntity scorecard1 = ScorecardEntity.builder()
                                     .accountIdentifier(accountidentifier)
                                     .identifier("sc1")
                                     .name("Scorecard 1")
                                     .description("Desc 1")
                                     .filter(filter)
                                     .published(true)
                                     .build();
    List<ScorecardAndChecks> scAndChecks =
        List.of(ScorecardAndChecks.builder().scorecard(scorecard1).checks(List.of()).build());
    when(scorecardService.getAllScorecardAndChecks(accountidentifier, null)).thenReturn(scAndChecks);

    ScopeTopology topology = buildTopology();
    when(scopeTopologyCache.get(accountidentifier)).thenReturn(topology);
    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(catalogServiceHelper.getKindScopeIdentifier("component:account.org1.proj1/my-service"))
        .thenReturn(org.apache.commons.lang3.tuple.Triple.of("component", "account.org1.proj1", "my-service"));

    InlineCatalogEntity catalogEntity = InlineCatalogEntity.builder().build();
    catalogEntity.setKind("component");
    catalogEntity.setIdentifier("my-service");
    catalogEntity.setAccountIdentifier(accountidentifier);
    when(catalogServiceHelper.catalogEntity("proj1UniqueId", "component", "my-service")).thenReturn(catalogEntity);

    when(applicabilityEngine.isApplicable(any(), any(), any())).thenReturn(true);
    when(scoreRepository.getLatestComputedScoreForEntityAndScorecard(
             eq(accountidentifier), anyString(), eq("sc1"), eq(true)))
        .thenReturn(null);

    AggregationResults<ScoreEntityByScorecardIdentifier> mockResults = mock(AggregationResults.class);
    when(scoreRepository.getAllLatestScoresByScorecardsForAnEntity(
             accountidentifier, "component:account.org1.proj1/my-service", true))
        .thenReturn(mockResults);
    when(mockResults.getMappedResults()).thenReturn(new ArrayList<>());

    scoreServiceimpl.getScoresSummaryForAnEntityV2(accountidentifier, "component:account.org1.proj1/my-service");
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetScoresSummaryForAnEntityV2_allScorecardsApplicable() {
    ScorecardFilter filter1 = new ScorecardFilter();
    filter1.setScopes(List.of("account.*"));
    filter1.setKind("component");

    ScorecardFilter filter2 = new ScorecardFilter();
    filter2.setScopes(List.of("account.*"));
    filter2.setKind("component");

    ScorecardEntity scorecard1 = ScorecardEntity.builder()
                                     .accountIdentifier(accountidentifier)
                                     .identifier("sc1")
                                     .name("Scorecard 1")
                                     .description("Desc 1")
                                     .filter(filter1)
                                     .published(true)
                                     .build();
    ScorecardEntity scorecard2 = ScorecardEntity.builder()
                                     .accountIdentifier(accountidentifier)
                                     .identifier("sc2")
                                     .name("Scorecard 2")
                                     .description("Desc 2")
                                     .filter(filter2)
                                     .published(true)
                                     .build();
    List<ScorecardAndChecks> scAndChecks =
        List.of(ScorecardAndChecks.builder().scorecard(scorecard1).checks(List.of()).build(),
            ScorecardAndChecks.builder().scorecard(scorecard2).checks(List.of()).build());
    when(scorecardService.getAllScorecardAndChecks(accountidentifier, null)).thenReturn(scAndChecks);

    ScopeTopology topology = buildTopology();
    when(scopeTopologyCache.get(accountidentifier)).thenReturn(topology);
    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(catalogServiceHelper.getKindScopeIdentifier("component:account.org1.proj1/my-service"))
        .thenReturn(org.apache.commons.lang3.tuple.Triple.of("component", "account.org1.proj1", "my-service"));

    InlineCatalogEntity catalogEntity = InlineCatalogEntity.builder().build();
    catalogEntity.setKind("component");
    catalogEntity.setIdentifier("my-service");
    catalogEntity.setAccountIdentifier(accountidentifier);
    when(catalogServiceHelper.catalogEntity("proj1UniqueId", "component", "my-service")).thenReturn(catalogEntity);

    when(applicabilityEngine.isApplicable(any(), any(), any())).thenReturn(true);

    ScoreEntity score1 = ScoreEntity.builder().id("score1").score(80).build();
    ScoreEntity score2 = ScoreEntity.builder().id("score2").score(90).build();
    when(scoreRepository.getLatestComputedScoreForEntityAndScorecard(
             eq(accountidentifier), anyString(), eq("sc1"), eq(true)))
        .thenReturn(score1);
    when(scoreRepository.getLatestComputedScoreForEntityAndScorecard(
             eq(accountidentifier), anyString(), eq("sc2"), eq(true)))
        .thenReturn(score2);

    when(asyncScoreComputationService.getRecalibrateInfo(any(), any(), any())).thenReturn(null);

    AggregationResults<ScoreEntityByScorecardIdentifier> mockResults = mock(AggregationResults.class);
    when(scoreRepository.getAllLatestScoresByScorecardsForAnEntity(
             accountidentifier, "component:account.org1.proj1/my-service", true))
        .thenReturn(mockResults);
    when(mockResults.getMappedResults())
        .thenReturn(
            List.of(ScoreEntityByScorecardIdentifier.builder().scorecardIdentifier("sc1").scoreEntity(score1).build(),
                ScoreEntityByScorecardIdentifier.builder().scorecardIdentifier("sc2").scoreEntity(score2).build()));

    List<ScorecardSummaryInfo> results =
        scoreServiceimpl.getScoresSummaryForAnEntityV2(accountidentifier, "component:account.org1.proj1/my-service");

    assertEquals(2, results.size());
    results.sort(Comparator.comparingInt(ScorecardSummaryInfo::getScore));
    assertEquals("Scorecard 1", results.get(0).getScorecardName());
    assertEquals(80, (int) results.get(0).getScore());
    assertEquals("Scorecard 2", results.get(1).getScorecardName());
    assertEquals(90, (int) results.get(1).getScore());
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetScoresSummaryForAnEntityV2_partiallyApplicable() {
    ScorecardFilter filter1 = new ScorecardFilter();
    filter1.setScopes(List.of("account.*"));
    filter1.setKind("component");

    ScorecardFilter filter2 = new ScorecardFilter();
    filter2.setScopes(List.of("account.org2"));
    filter2.setKind("service");

    ScorecardEntity scorecard1 = ScorecardEntity.builder()
                                     .accountIdentifier(accountidentifier)
                                     .identifier("sc1")
                                     .name("Scorecard 1")
                                     .description("Desc 1")
                                     .filter(filter1)
                                     .published(true)
                                     .build();
    ScorecardEntity scorecard2 = ScorecardEntity.builder()
                                     .accountIdentifier(accountidentifier)
                                     .identifier("sc2")
                                     .name("Scorecard 2")
                                     .description("Desc 2")
                                     .filter(filter2)
                                     .published(true)
                                     .build();
    List<ScorecardAndChecks> scAndChecks =
        List.of(ScorecardAndChecks.builder().scorecard(scorecard1).checks(List.of()).build(),
            ScorecardAndChecks.builder().scorecard(scorecard2).checks(List.of()).build());
    when(scorecardService.getAllScorecardAndChecks(accountidentifier, null)).thenReturn(scAndChecks);

    ScopeTopology topology = buildTopology();
    when(scopeTopologyCache.get(accountidentifier)).thenReturn(topology);
    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(catalogServiceHelper.getKindScopeIdentifier("component:account.org1.proj1/my-service"))
        .thenReturn(org.apache.commons.lang3.tuple.Triple.of("component", "account.org1.proj1", "my-service"));

    InlineCatalogEntity catalogEntity = InlineCatalogEntity.builder().build();
    catalogEntity.setKind("component");
    catalogEntity.setIdentifier("my-service");
    catalogEntity.setAccountIdentifier(accountidentifier);
    when(catalogServiceHelper.catalogEntity("proj1UniqueId", "component", "my-service")).thenReturn(catalogEntity);

    // First scorecard is applicable, second is not
    when(applicabilityEngine.isApplicable(eq(filter1), any(), any())).thenReturn(true);
    when(applicabilityEngine.isApplicable(eq(filter2), any(), any())).thenReturn(false);

    ScoreEntity score1 = ScoreEntity.builder().id("score1").score(75).build();
    when(scoreRepository.getLatestComputedScoreForEntityAndScorecard(
             eq(accountidentifier), anyString(), eq("sc1"), eq(true)))
        .thenReturn(score1);

    when(asyncScoreComputationService.getRecalibrateInfo(any(), any(), any())).thenReturn(null);

    AggregationResults<ScoreEntityByScorecardIdentifier> mockResults = mock(AggregationResults.class);
    when(scoreRepository.getAllLatestScoresByScorecardsForAnEntity(
             accountidentifier, "component:account.org1.proj1/my-service", true))
        .thenReturn(mockResults);
    when(mockResults.getMappedResults())
        .thenReturn(
            List.of(ScoreEntityByScorecardIdentifier.builder().scorecardIdentifier("sc1").scoreEntity(score1).build()));

    List<ScorecardSummaryInfo> results =
        scoreServiceimpl.getScoresSummaryForAnEntityV2(accountidentifier, "component:account.org1.proj1/my-service");

    assertEquals(1, results.size());
    assertEquals("Scorecard 1", results.get(0).getScorecardName());
    assertEquals(75, (int) results.get(0).getScore());
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetScoresSummaryForAnEntityV2_scopeFilterValidation() {
    // Scorecard 1: scopes to account.org1.* (should match entity in org1.proj1)
    ScorecardFilter filter1 = new ScorecardFilter();
    filter1.setScopes(List.of("account.org1.*"));
    filter1.setKind("component");

    // Scorecard 2: scopes to account.org2 only (should NOT match entity in org1.proj1)
    ScorecardFilter filter2 = new ScorecardFilter();
    filter2.setScopes(List.of("account.org2"));
    filter2.setKind("component");

    // Scorecard 3: scopes to account (account-level only, no org/project)
    ScorecardFilter filter3 = new ScorecardFilter();
    filter3.setScopes(List.of("account"));
    filter3.setKind("component");

    // Scorecard 4: null scopes (defaults to account.*)
    ScorecardFilter filter4 = new ScorecardFilter();
    filter4.setScopes(null);
    filter4.setKind("component");

    ScorecardEntity scorecard1 = ScorecardEntity.builder()
                                     .accountIdentifier(accountidentifier)
                                     .identifier("sc1")
                                     .name("Org1 Wildcard Scorecard")
                                     .description("Scoped to org1.*")
                                     .filter(filter1)
                                     .published(true)
                                     .build();
    ScorecardEntity scorecard2 = ScorecardEntity.builder()
                                     .accountIdentifier(accountidentifier)
                                     .identifier("sc2")
                                     .name("Org2 Only Scorecard")
                                     .description("Scoped to org2 only")
                                     .filter(filter2)
                                     .published(true)
                                     .build();
    ScorecardEntity scorecard3 = ScorecardEntity.builder()
                                     .accountIdentifier(accountidentifier)
                                     .identifier("sc3")
                                     .name("Account Level Scorecard")
                                     .description("Account scope only")
                                     .filter(filter3)
                                     .published(true)
                                     .build();
    ScorecardEntity scorecard4 = ScorecardEntity.builder()
                                     .accountIdentifier(accountidentifier)
                                     .identifier("sc4")
                                     .name("Default Scopes Scorecard")
                                     .description("Null scopes defaults to account.*")
                                     .filter(filter4)
                                     .published(true)
                                     .build();

    List<ScorecardAndChecks> scAndChecks =
        List.of(ScorecardAndChecks.builder().scorecard(scorecard1).checks(List.of()).build(),
            ScorecardAndChecks.builder().scorecard(scorecard2).checks(List.of()).build(),
            ScorecardAndChecks.builder().scorecard(scorecard3).checks(List.of()).build(),
            ScorecardAndChecks.builder().scorecard(scorecard4).checks(List.of()).build());
    when(scorecardService.getAllScorecardAndChecks(accountidentifier, null)).thenReturn(scAndChecks);

    ScopeTopology topology = buildTopology();
    when(scopeTopologyCache.get(accountidentifier)).thenReturn(topology);
    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(catalogServiceHelper.getKindScopeIdentifier("component:account.org1.proj1/my-service"))
        .thenReturn(org.apache.commons.lang3.tuple.Triple.of("component", "account.org1.proj1", "my-service"));

    InlineCatalogEntity catalogEntity = InlineCatalogEntity.builder().build();
    catalogEntity.setKind("component");
    catalogEntity.setIdentifier("my-service");
    catalogEntity.setAccountIdentifier(accountidentifier);
    catalogEntity.setOrgIdentifier("org1");
    catalogEntity.setProjectIdentifier("proj1");
    when(catalogServiceHelper.catalogEntity("proj1UniqueId", "component", "my-service")).thenReturn(catalogEntity);

    // sc1 (org1.*) → applicable, sc2 (org2) → not applicable, sc3 (account) → not applicable, sc4 (account.*) →
    // applicable
    when(applicabilityEngine.isApplicable(eq(filter1), any(), any())).thenReturn(true);
    when(applicabilityEngine.isApplicable(eq(filter2), any(), any())).thenReturn(false);
    when(applicabilityEngine.isApplicable(eq(filter3), any(), any())).thenReturn(false);
    when(applicabilityEngine.isApplicable(eq(filter4), any(), any())).thenReturn(true);

    ScoreEntity score1 = ScoreEntity.builder().id("score1").score(70).build();
    ScoreEntity score4 = ScoreEntity.builder().id("score4").score(95).build();
    when(scoreRepository.getLatestComputedScoreForEntityAndScorecard(
             eq(accountidentifier), anyString(), eq("sc1"), eq(true)))
        .thenReturn(score1);
    when(scoreRepository.getLatestComputedScoreForEntityAndScorecard(
             eq(accountidentifier), anyString(), eq("sc4"), eq(true)))
        .thenReturn(score4);

    when(asyncScoreComputationService.getRecalibrateInfo(any(), any(), any())).thenReturn(null);

    AggregationResults<ScoreEntityByScorecardIdentifier> mockResults = mock(AggregationResults.class);
    when(scoreRepository.getAllLatestScoresByScorecardsForAnEntity(
             accountidentifier, "component:account.org1.proj1/my-service", true))
        .thenReturn(mockResults);
    when(mockResults.getMappedResults())
        .thenReturn(
            List.of(ScoreEntityByScorecardIdentifier.builder().scorecardIdentifier("sc1").scoreEntity(score1).build(),
                ScoreEntityByScorecardIdentifier.builder().scorecardIdentifier("sc4").scoreEntity(score4).build()));

    List<ScorecardSummaryInfo> results =
        scoreServiceimpl.getScoresSummaryForAnEntityV2(accountidentifier, "component:account.org1.proj1/my-service");

    assertEquals(2, results.size());
    results.sort(Comparator.comparingInt(ScorecardSummaryInfo::getScore));
    assertEquals("Org1 Wildcard Scorecard", results.get(0).getScorecardName());
    assertEquals(70, (int) results.get(0).getScore());
    assertEquals("Default Scopes Scorecard", results.get(1).getScorecardName());
    assertEquals(95, (int) results.get(1).getScore());
  }

  private ScopeTopology buildTopology() {
    Map<String, String> proj1Projects = new HashMap<>();
    proj1Projects.put("proj1", "proj1UniqueId");
    proj1Projects.put("proj2", "proj2UniqueId");

    Map<String, String> proj2Projects = new HashMap<>();
    proj2Projects.put("proj3", "proj3UniqueId");

    Map<String, ScopeTopology.OrgNode> orgs = new HashMap<>();
    orgs.put("org1", ScopeTopology.OrgNode.builder().uniqueId("org1UniqueId").projects(proj1Projects).build());
    orgs.put("org2", ScopeTopology.OrgNode.builder().uniqueId("org2UniqueId").projects(proj2Projects).build());

    return ScopeTopology.builder().accountUniqueId(accountidentifier).orgs(orgs).build();
  }

  public void getScoreCardandChecks() {
    ScorecardEntity scorecard1 = ScorecardEntity.builder()
                                     .accountIdentifier("account2")
                                     .identifier("scorecard1")
                                     .name("Scorecard 1")
                                     .description("Description for Scorecard 1")
                                     .published(true)
                                     .isDeleted(false)
                                     .build();
    ScorecardEntity scorecard2 = ScorecardEntity.builder()
                                     .accountIdentifier("account2")
                                     .identifier("scorecard2")
                                     .name("Scorecard 2")
                                     .description("Description for Scorecard 2")
                                     .published(true)
                                     .isDeleted(false)
                                     .build();
    CheckEntity check1 = CheckEntity.builder().name("checkentityname").identifier("check1").build();
    CheckEntity check2 = CheckEntity.builder().name("checkentityname").identifier("check2").build();
    ScorecardAndChecks scorecardAndChecks1 =
        ScorecardAndChecks.builder().scorecard(scorecard1).checks(List.of(check1)).build();
    ScorecardAndChecks scorecardAndChecks2 =
        ScorecardAndChecks.builder().scorecard(scorecard2).checks(List.of(check2)).build();
    scorecardAndChecks = List.of(scorecardAndChecks1, scorecardAndChecks2);
  }

  public void getbackstagecatalogentity() {
    BackstageCatalogEntity entity = BackstageCatalogComponentEntity.builder()
                                        .id("123")
                                        .accountIdentifier("account123")
                                        .entityUid("uid123")
                                        .metadata(Map.of("key1", "value1"))
                                        .kind("API")
                                        .relations(Set.of(BackstageCatalogEntity.Relation.builder()
                                                              .type("type1")
                                                              .targetRef("targetRef1")
                                                              .target(BackstageCatalogEntity.Target.builder()
                                                                          .kind("kind1")
                                                                          .namespace("namespace1")
                                                                          .name("name1")
                                                                          .build())
                                                              .build()))
                                        .build();
    mockEntities.add(entity);
  }

  public String loadResourceFileAsString(String resourcePath) {
    try {
      return Resources.toString(Resources.getResource(resourcePath), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UnexpectedException(
          "Error in loading resource " + resourcePath + " as string. Error = " + e.getMessage());
    }
  }
}
