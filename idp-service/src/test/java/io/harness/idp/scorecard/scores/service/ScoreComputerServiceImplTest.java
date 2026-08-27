/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.scores.service;
import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.rule.OwnerRule.AGNIVA;
import static io.harness.rule.OwnerRule.NITESH_GAHLOT;
import static io.harness.rule.OwnerRule.VIGNESWARA;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.clients.BackstageResourceClient;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.metrics.IdpIteratorMetricRecorder;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.repositories.DataPointsRepository;
import io.harness.idp.scorecard.datasources.providers.DataSourceProviderFactory;
import io.harness.idp.scorecard.datasources.providers.DataSourceProviderV1;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;
import io.harness.idp.scorecard.scorecards.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.events.ScorecardRecalibrateEvent;
import io.harness.idp.scorecard.scorecards.repositories.ScorecardRepository;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.idp.scorecard.scores.repositories.ScoreEntityByEntityIdentifier;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;
import io.harness.idp.scorecard.tiergroups.entity.TierGroupEntity;
import io.harness.idp.scorecard.tiergroups.service.TierGroupService;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.CGRestUtils;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CheckDetails;
import io.harness.spec.server.idp.v1.model.InputValue;
import io.harness.spec.server.idp.v1.model.Rule;
import io.harness.spec.server.idp.v1.model.ScoreTier;
import io.harness.spec.server.idp.v1.model.ScorecardDetails;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsResponse;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateInfo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.name.Named;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import retrofit2.Call;
import retrofit2.Response;
@OwnedBy(HarnessTeam.IDP)
public class ScoreComputerServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "123";
  private static final String FILTER_TYPE_SERVICE = "Service";
  private static final String CHECK_IDENTIFIER1 = "c1";
  private static final String CHECK_IDENTIFIER2 = "c2";
  private static final String SCORECARD_IDENTIFIER1 = "cw";
  private static final String SCORECARD_IDENTIFIER2 = "ew";
  private static final String TIER_GROUP_IDENTIFIER = "default_tiers";
  private static final String CUSTOM_TIER_GROUP_ID = "custom_tiers";
  private static final String DATA_SOURCE_IDENTIFIER = "ds1";
  private static final String DATA_SOURCE_LOCATION_IDENTIFIER = "dsl1";
  private static final String DATA_POINT_IDENTIFIER1 = "dp1";
  private static final String RULE_IDENTIFIER1 = "rule1";
  private static final String RULE_IDENTIFIER2 = "rule2";
  private static final String DATA_POINT_IDENTIFIER2 = "dp2";
  private static final String OPERATOR1 = "==";
  private static final String OPERATOR2 = "==";
  private static final String VALUE = "true";
  private static final String COMPONENT = "Component";
  private static final String DEFAULT = "default";
  private static final String IDP_SERVICE_NAME = "idp-service";
  private static final String PMS_SERVICE_NAME = "pms-service";
  private static final String ENTITY_UID1 = DEFAULT + "/" + COMPONENT + "/" + IDP_SERVICE_NAME;
  private static final String ENTITY_UID2 = DEFAULT + "/" + COMPONENT + "/" + PMS_SERVICE_NAME;
  private static final String OWNER = "owner1";
  private static final String TAG = "tag1";
  private static final String LIFECYCLE = "prod";
  private static final String INPUT_VALUE = "v1";
  @Mock ExecutorService executorService;
  @Mock ScorecardService scorecardService;
  @Mock BackstageResourceClient backstageResourceClient;
  @Mock DataSourceProviderFactory dataSourceProviderFactory;
  @Mock ScoreRepository scoreRepository;
  @Mock ScorecardRepository scorecardRepository;
  @Mock DataPointsRepository datapointRepository;
  @Mock DataSourceProviderV1 dataSourceProvider;
  @Mock ConfigReader configReader;
  @Mock private AsyncScoreComputationService asyncScoreComputationService;
  @Mock private OutboxService outboxService;
  @InjectMocks ScoreComputerServiceImpl scoreComputerService;
  @Mock @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  private Call<Object> call;
  AutoCloseable openMocks;
  static Gson gson = new Gson();
  @Captor private ArgumentCaptor<ScoreEntity> scoreCaptor;
  @Mock AccountClient accountClient;
  @Mock IdpCommonService idpCommonService;
  @Mock TierGroupService tierGroupService;
  @Mock IdpIteratorMetricRecorder idpIteratorMetricRecorder;
  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    call = mock(Call.class);
    scoreComputerService.userExecutorService = executorService; // Manually inject the mock for the second executor
    MockedStatic<CGRestUtils> mockRestUtils = mockStatic(CGRestUtils.class);
    mockRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(false);
    when(tierGroupService.getActiveTierGroup(anyString(), anyString())).thenReturn(getTierGroupEntity());
    when(tierGroupService.resolveScoreTier(any(TierGroupEntity.class), anyString(), anyInt()))
        .thenReturn(Optional.empty());
    when(idpCommonService.idpScorecardTiersEnabled(ACCOUNT_ID)).thenReturn(true);
  }
  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testComputeScores() throws IOException, NoSuchAlgorithmException, KeyManagementException {
    List<String> scorecardIdentifiers = Collections.emptyList();
    List<String> entityIdentifiers = Collections.emptyList();
    ScorecardAndChecks scorecardAndChecks1 =
        ScorecardAndChecks.builder().scorecard(getMockScorecardCustomWeights()).checks(getMockChecks()).build();
    ScorecardAndChecks scorecardAndChecks2 =
        ScorecardAndChecks.builder().scorecard(getMockScorecardEqualWeights()).checks(getMockChecks()).build();
    Response<Object> response = getMockServicesApiResponse();
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    DataPointEntity datapoint1 = getMockDataPoint(DATA_POINT_IDENTIFIER1, false);
    DataPointEntity datapoint2 = getMockDataPoint(DATA_POINT_IDENTIFIER2, true);
    Map<String, Map<String, Object>> data1 = mockResponseData(true, false);
    Map<String, Map<String, Object>> data2 = mockResponseData(false, true);
    List<ScorecardAndChecks> scorecardAndChecks = new ArrayList<>();
    scorecardAndChecks.add(scorecardAndChecks1);
    scorecardAndChecks.add(scorecardAndChecks2);
    when(scorecardService.getAllScorecardAndChecks(ACCOUNT_ID, scorecardIdentifiers)).thenReturn(scorecardAndChecks);
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntities(anyString())).thenReturn(call);
    when(configReader.fetchAllConfigs(ACCOUNT_ID)).thenReturn(null);
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
    when(datapointRepository.findByIdentifierIn(Set.of(DATA_POINT_IDENTIFIER1, DATA_POINT_IDENTIFIER2)))
        .thenReturn(List.of(datapoint1, datapoint2));
    when(dataSourceProviderFactory.getProvider(DATA_SOURCE_IDENTIFIER, false)).thenReturn(dataSourceProvider);
    when(dataSourceProvider.fetchData(eq(ACCOUNT_ID), any(BackstageCatalogComponentEntity.class), anyList(), any()))
        .thenReturn(data1)
        .thenReturn(data2);
    when(idpCommonService.idpV2Enabled(ACCOUNT_ID)).thenReturn(false);
    stubEnsureScorecardTierGroupIdentifier();
    when(tierGroupService.resolveScoreTier(any(TierGroupEntity.class), eq(TIER_GROUP_IDENTIFIER), anyInt()))
        .thenAnswer(invocation
            -> Optional.of(new ScoreTier()
                               .tierName("Tier " + invocation.getArgument(2, Integer.class))
                               .tierGroupIdentifier(TIER_GROUP_IDENTIFIER)));
    scoreComputerService.computeScores(ACCOUNT_ID, scorecardIdentifiers, entityIdentifiers);
    verify(scoreRepository, times(3)).save(scoreCaptor.capture());
    List<ScoreEntity> scores = scoreCaptor.getAllValues();
    assertEquals(3, scores.size());
    // uid1
    assertEquals(40, scores.get(0).getScore()); // custom weights scorecard; c1(2) = true, c2(3) = false
    assertEquals(60, scores.get(1).getScore()); // equal weights scorecard; c1(1) = true, c2(1) = false
    // uid2
    assertEquals(50, scores.get(2).getScore()); // custom weights scorecard; c1(2) = false, c2(3) = true
    assertEquals("Tier 40", scores.get(0).getTierName());
    assertEquals(TIER_GROUP_IDENTIFIER, scores.get(0).getTierGroupIdentifier());
    verify(backstageResourceClient, times(2)).getCatalogEntities(anyString());
  }
  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testComputeScoresWhenNoScorecards() {
    List<String> scorecardIdentifiers = Collections.emptyList();
    List<String> entityIdentifiers = Collections.emptyList();
    when(scorecardService.getAllScorecardAndChecks(ACCOUNT_ID, scorecardIdentifiers))
        .thenReturn(Collections.emptyList());
    when(idpCommonService.idpV2Enabled(ACCOUNT_ID)).thenReturn(false);
    stubEnsureScorecardTierGroupIdentifier();
    scoreComputerService.computeScores(ACCOUNT_ID, scorecardIdentifiers, entityIdentifiers);
    verify(scoreRepository, never()).save(any());
  }
  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testComputeScoresWhenNoEntities() throws IOException {
    List<String> scorecardIdentifiers = Collections.emptyList();
    List<String> entityIdentifiers = Collections.emptyList();
    ScorecardAndChecks scorecardAndChecks =
        ScorecardAndChecks.builder().scorecard(getMockScorecardCustomWeights()).checks(getMockChecks()).build();
    Response<Object> response = Response.success(Collections.emptyList());
    List<ScorecardAndChecks> scorecardAndChecksList = new ArrayList<>();
    scorecardAndChecksList.add(scorecardAndChecks);
    when(scorecardService.getAllScorecardAndChecks(ACCOUNT_ID, scorecardIdentifiers))
        .thenReturn(scorecardAndChecksList);
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntities(anyString())).thenReturn(call);
    when(idpCommonService.idpV2Enabled(ACCOUNT_ID)).thenReturn(false);
    stubEnsureScorecardTierGroupIdentifier();
    when(tierGroupService.getActiveTierGroup(ACCOUNT_ID, TIER_GROUP_IDENTIFIER)).thenReturn(getTierGroupEntity());
    scoreComputerService.computeScores(ACCOUNT_ID, scorecardIdentifiers, entityIdentifiers);
    verify(scoreRepository, never()).save(any());
    verify(scorecardRepository)
        .updateScoreCounts(eq(ACCOUNT_ID), eq(SCORECARD_IDENTIFIER1), eq(0),
            argThat(tierCounts
                -> tierCounts.size() == 2 && "Bronze".equals(tierCounts.get(0).getTierName())
                    && tierCounts.get(0).getComponentCount() == 0 && "Gold".equals(tierCounts.get(1).getTierName())
                    && tierCounts.get(1).getComponentCount() == 0),
            anyLong());
    verify(idpIteratorMetricRecorder).recordSuccess("ScorecardCountRefresh", ACCOUNT_ID);
    verify(idpIteratorMetricRecorder, never()).recordFailure(anyString(), anyString());
    verify(backstageResourceClient).getCatalogEntities(anyString());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testComputeScoresRecordsRefreshFailureMetricWhenCountRefreshFails() throws IOException {
    List<String> scorecardIdentifiers = Collections.emptyList();
    List<String> entityIdentifiers = Collections.emptyList();
    ScorecardAndChecks scorecardAndChecks =
        ScorecardAndChecks.builder().scorecard(getMockScorecardCustomWeights()).checks(getMockChecks()).build();
    when(scorecardService.getAllScorecardAndChecks(ACCOUNT_ID, scorecardIdentifiers))
        .thenReturn(List.of(scorecardAndChecks));
    when(call.execute()).thenReturn(Response.success(Collections.emptyList()));
    when(backstageResourceClient.getCatalogEntities(anyString())).thenReturn(call);
    when(idpCommonService.idpV2Enabled(ACCOUNT_ID)).thenReturn(false);
    stubEnsureScorecardTierGroupIdentifier();
    when(tierGroupService.getActiveTierGroup(ACCOUNT_ID, TIER_GROUP_IDENTIFIER)).thenReturn(null);

    scoreComputerService.computeScores(ACCOUNT_ID, scorecardIdentifiers, entityIdentifiers);

    verify(scorecardRepository, never()).updateScoreCounts(any(), any(), anyInt(), anyList(), anyLong());
    verify(idpIteratorMetricRecorder).recordFailure("ScorecardCountRefresh", ACCOUNT_ID);
    verify(idpIteratorMetricRecorder, never()).recordSuccess(anyString(), anyString());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testComputeScoresDoesNotPersistCountsWhenTierAnalyticsDisabled() throws IOException {
    List<String> scorecardIdentifiers = Collections.emptyList();
    List<String> entityIdentifiers = Collections.emptyList();
    ScorecardAndChecks scorecardAndChecks =
        ScorecardAndChecks.builder().scorecard(getMockScorecardCustomWeights()).checks(getMockChecks()).build();
    when(scorecardService.getAllScorecardAndChecks(ACCOUNT_ID, scorecardIdentifiers))
        .thenReturn(List.of(scorecardAndChecks));
    when(call.execute()).thenReturn(Response.success(Collections.emptyList()));
    when(backstageResourceClient.getCatalogEntities(anyString())).thenReturn(call);
    when(idpCommonService.idpV2Enabled(ACCOUNT_ID)).thenReturn(false);
    when(idpCommonService.idpScorecardTiersEnabled(ACCOUNT_ID)).thenReturn(false);

    scoreComputerService.computeScores(ACCOUNT_ID, scorecardIdentifiers, entityIdentifiers);

    verify(scorecardRepository, never()).updateScoreCounts(any(), any(), anyInt(), anyList(), anyLong());
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testcomputeScoresAsync() {
    String accountIdentifier = "account1";
    String scorecardIdentifier = "scorecard1";
    String entityIdentifier = "entity1";
    ScorecardRecalibrateInfo recalibrateInfo = new ScorecardRecalibrateInfo();
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      return ((TransactionCallback<ScorecardRecalibrateInfo>) invocation.getArgument(0)).doInTransaction(null);
    });
    when(asyncScoreComputationService.getRecalibrateInfo(accountIdentifier, scorecardIdentifier, entityIdentifier))
        .thenReturn(recalibrateInfo);
    ScorecardRecalibrateInfo result =
        scoreComputerService.computeScoresAsync(accountIdentifier, scorecardIdentifier, entityIdentifier);
    assertNotNull(result);
    assertEquals(recalibrateInfo, result);
    verify(asyncScoreComputationService).getRecalibrateInfo(accountIdentifier, scorecardIdentifier, entityIdentifier);
  }
  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testComputeScoresAsync_WhenRecalibrateInfoDoesNotExist() {
    String accountIdentifier = "account1";
    String scorecardIdentifier = "scorecard1";
    String entityIdentifier = "entity1";
    ScorecardDetailsResponse scorecardDetailsResponse = mock(ScorecardDetailsResponse.class);
    ScorecardDetails scorecardDetails = mock(ScorecardDetails.class);
    ScorecardRecalibrateInfo recalibrateInfo = new ScorecardRecalibrateInfo();
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      return ((TransactionCallback<ScorecardRecalibrateInfo>) invocation.getArgument(0)).doInTransaction(null);
    });
    when(asyncScoreComputationService.getRecalibrateInfo(accountIdentifier, scorecardIdentifier, entityIdentifier))
        .thenReturn(null);
    when(scorecardService.getScorecardDetails(accountIdentifier, scorecardIdentifier))
        .thenReturn(scorecardDetailsResponse);
    when(scorecardDetailsResponse.getScorecard()).thenReturn(scorecardDetails);
    when(asyncScoreComputationService.logScoreComputationRequestAndPublishEvent(
             accountIdentifier, scorecardIdentifier, entityIdentifier))
        .thenReturn(recalibrateInfo);
    ScorecardRecalibrateInfo result =
        scoreComputerService.computeScoresAsync(accountIdentifier, scorecardIdentifier, entityIdentifier);
    assertNotNull(result);
    assertEquals(recalibrateInfo, result);
    verify(asyncScoreComputationService).getRecalibrateInfo(accountIdentifier, scorecardIdentifier, entityIdentifier);
    verify(scorecardService).getScorecardDetails(accountIdentifier, scorecardIdentifier);
    verify(outboxService).save(any(ScorecardRecalibrateEvent.class));
    verify(asyncScoreComputationService)
        .logScoreComputationRequestAndPublishEvent(accountIdentifier, scorecardIdentifier, entityIdentifier);
  }
  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testComputeScoresForAnEntity() throws IOException, NoSuchAlgorithmException, KeyManagementException {
    List<String> scorecardIdentifiers = Collections.emptyList();
    List<String> entityIdentifiers = Collections.singletonList(ENTITY_UID1);
    ScorecardAndChecks scorecardAndChecks =
        ScorecardAndChecks.builder().scorecard(getMockScorecardCustomWeights()).checks(getMockChecks()).build();
    Response<Object> response = getMockServicesApiResponse();
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    DataPointEntity datapoint1 = getMockDataPoint(DATA_POINT_IDENTIFIER1, false);
    DataPointEntity datapoint2 = getMockDataPoint(DATA_POINT_IDENTIFIER2, true);
    Map<String, Map<String, Object>> data = mockResponseData(false, false);
    List<ScorecardAndChecks> scorecardAndChecksList = new ArrayList<>();
    scorecardAndChecksList.add(scorecardAndChecks);
    when(scorecardService.getAllScorecardAndChecks(ACCOUNT_ID, scorecardIdentifiers))
        .thenReturn(scorecardAndChecksList);
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntities(anyString())).thenReturn(call);
    when(configReader.fetchAllConfigs(ACCOUNT_ID)).thenReturn(null);
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
    when(datapointRepository.findByIdentifierIn(Set.of(DATA_POINT_IDENTIFIER1, DATA_POINT_IDENTIFIER2)))
        .thenReturn(List.of(datapoint1, datapoint2));
    when(dataSourceProviderFactory.getProvider(DATA_SOURCE_IDENTIFIER, false)).thenReturn(dataSourceProvider);
    when(dataSourceProvider.fetchData(eq(ACCOUNT_ID), any(BackstageCatalogComponentEntity.class), anyList(), any()))
        .thenReturn(data);
    // Correctly mock the transactionTemplate to execute the callback
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(mock(TransactionStatus.class));
    });
    when(idpCommonService.idpV2Enabled(ACCOUNT_ID)).thenReturn(false);
    stubEnsureScorecardTierGroupIdentifier();
    scoreComputerService.computeScores(ACCOUNT_ID, scorecardIdentifiers, entityIdentifiers);
    verify(scoreRepository).save(scoreCaptor.capture());
    assertEquals(0, scoreCaptor.getValue().getScore());
    verify(tierGroupService).resolveScoreTier(any(TierGroupEntity.class), eq("default_tiers"), eq(0));
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void computeScoresPersistsFullTierSnapshot()
      throws IOException, NoSuchAlgorithmException, KeyManagementException {
    List<String> scorecardIdentifiers = Collections.emptyList();
    List<String> entityIdentifiers = Collections.singletonList(ENTITY_UID1);
    ScorecardAndChecks scorecardAndChecks =
        ScorecardAndChecks.builder().scorecard(getMockScorecardCustomWeights()).checks(getMockChecks()).build();
    Response<Object> response = getMockServicesApiResponse();
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    DataPointEntity datapoint1 = getMockDataPoint(DATA_POINT_IDENTIFIER1, false);
    DataPointEntity datapoint2 = getMockDataPoint(DATA_POINT_IDENTIFIER2, true);
    Map<String, Map<String, Object>> data = mockResponseData(true, false);
    List<ScorecardAndChecks> scorecardAndChecksList = new ArrayList<>();
    scorecardAndChecksList.add(scorecardAndChecks);
    when(scorecardService.getAllScorecardAndChecks(ACCOUNT_ID, scorecardIdentifiers))
        .thenReturn(scorecardAndChecksList);
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntities(anyString())).thenReturn(call);
    when(configReader.fetchAllConfigs(ACCOUNT_ID)).thenReturn(null);
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
    when(datapointRepository.findByIdentifierIn(Set.of(DATA_POINT_IDENTIFIER1, DATA_POINT_IDENTIFIER2)))
        .thenReturn(List.of(datapoint1, datapoint2));
    when(dataSourceProviderFactory.getProvider(DATA_SOURCE_IDENTIFIER, false)).thenReturn(dataSourceProvider);
    when(dataSourceProvider.fetchData(eq(ACCOUNT_ID), any(BackstageCatalogComponentEntity.class), anyList(), any()))
        .thenReturn(data);
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(mock(TransactionStatus.class));
    });
    when(idpCommonService.idpV2Enabled(ACCOUNT_ID)).thenReturn(false);
    stubEnsureScorecardTierGroupIdentifier();
    when(tierGroupService.resolveScoreTier(any(TierGroupEntity.class), eq(TIER_GROUP_IDENTIFIER), anyInt()))
        .thenReturn(Optional.of(new ScoreTier()
                                    .tierName("Gold")
                                    .tierGroupIdentifier(TIER_GROUP_IDENTIFIER)
                                    .tierDescription("Gold tier")
                                    .tierIcon("https://example.com/gold.png")
                                    .tierColour("#FFD700")));
    when(tierGroupService.getActiveTierGroup(ACCOUNT_ID, TIER_GROUP_IDENTIFIER)).thenReturn(getTierGroupEntity());
    when(scoreRepository.getLatestScorePerEntityForScorecard(ACCOUNT_ID, SCORECARD_IDENTIFIER1))
        .thenReturn(List.of(ScoreEntityByEntityIdentifier.builder()
                                .entityIdentifier(ENTITY_UID1)
                                .scoreEntity(ScoreEntity.builder()
                                                 .entityIdentifier(ENTITY_UID1)
                                                 .score(75)
                                                 .lastComputedTimestamp(System.currentTimeMillis())
                                                 .build())
                                .build(),
            ScoreEntityByEntityIdentifier.builder()
                .entityIdentifier(ENTITY_UID2)
                .scoreEntity(ScoreEntity.builder()
                                 .entityIdentifier(ENTITY_UID2)
                                 .score(74)
                                 .lastComputedTimestamp(System.currentTimeMillis())
                                 .build())
                .build()));

    scoreComputerService.computeScores(ACCOUNT_ID, scorecardIdentifiers, entityIdentifiers);

    verify(scoreRepository).save(scoreCaptor.capture());
    ScoreEntity savedScore = scoreCaptor.getValue();
    assertEquals("Gold", savedScore.getTierName());
    assertEquals(TIER_GROUP_IDENTIFIER, savedScore.getTierGroupIdentifier());
    assertEquals("Gold tier", savedScore.getTierDescription());
    assertEquals("https://example.com/gold.png", savedScore.getTierIcon());
    assertEquals("#FFD700", savedScore.getTierColour());
    verify(scorecardRepository)
        .updateScoreCounts(eq(ACCOUNT_ID), eq(SCORECARD_IDENTIFIER1), eq(2),
            argThat(tierCounts
                -> tierCounts.size() == 2 && "Bronze".equals(tierCounts.get(0).getTierName())
                    && tierCounts.get(0).getMinScore() == 0 && tierCounts.get(0).getMaxScore() == 74
                    && tierCounts.get(0).getComponentCount() == 1 && "Gold".equals(tierCounts.get(1).getTierName())
                    && tierCounts.get(1).getMinScore() == 75 && tierCounts.get(1).getMaxScore() == 100
                    && "#FFD700".equals(tierCounts.get(1).getTierColour())
                    && tierCounts.get(1).getComponentCount() == 1),
            anyLong());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void computeScoresAssignsDefaultTierWhenScorecardHasNullTierGroup()
      throws IOException, NoSuchAlgorithmException, KeyManagementException {
    ScorecardEntity scorecardWithoutTier = getMockScorecardCustomWeights();
    scorecardWithoutTier.setTierGroupIdentifier(null);
    List<String> scorecardIdentifiers = Collections.emptyList();
    List<String> entityIdentifiers = Collections.singletonList(ENTITY_UID1);
    ScorecardAndChecks scorecardAndChecks =
        ScorecardAndChecks.builder().scorecard(scorecardWithoutTier).checks(getMockChecks()).build();
    Response<Object> response = getMockServicesApiResponse();
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    DataPointEntity datapoint1 = getMockDataPoint(DATA_POINT_IDENTIFIER1, false);
    DataPointEntity datapoint2 = getMockDataPoint(DATA_POINT_IDENTIFIER2, true);
    Map<String, Map<String, Object>> data = mockResponseData(true, false);
    List<ScorecardAndChecks> scorecardAndChecksList = new ArrayList<>();
    scorecardAndChecksList.add(scorecardAndChecks);
    when(scorecardService.getAllScorecardAndChecks(ACCOUNT_ID, scorecardIdentifiers))
        .thenReturn(scorecardAndChecksList);
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntities(anyString())).thenReturn(call);
    when(configReader.fetchAllConfigs(ACCOUNT_ID)).thenReturn(null);
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
    when(datapointRepository.findByIdentifierIn(Set.of(DATA_POINT_IDENTIFIER1, DATA_POINT_IDENTIFIER2)))
        .thenReturn(List.of(datapoint1, datapoint2));
    when(dataSourceProviderFactory.getProvider(DATA_SOURCE_IDENTIFIER, false)).thenReturn(dataSourceProvider);
    when(dataSourceProvider.fetchData(eq(ACCOUNT_ID), any(BackstageCatalogComponentEntity.class), anyList(), any()))
        .thenReturn(data);
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(mock(TransactionStatus.class));
    });
    when(idpCommonService.idpV2Enabled(ACCOUNT_ID)).thenReturn(false);
    stubEnsureScorecardTierGroupIdentifier();
    when(tierGroupService.resolveScoreTier(any(TierGroupEntity.class), eq(TIER_GROUP_IDENTIFIER), anyInt()))
        .thenReturn(Optional.of(new ScoreTier()
                                    .tierName("Bronze")
                                    .tierGroupIdentifier(TIER_GROUP_IDENTIFIER)
                                    .tierDescription("Bronze tier")
                                    .tierIcon("https://example.com/bronze.png")
                                    .tierColour("#CD7F32")));

    scoreComputerService.computeScores(ACCOUNT_ID, scorecardIdentifiers, entityIdentifiers);

    verify(scorecardService, times(2)).ensureScorecardTierGroupIdentifier(ACCOUNT_ID, scorecardWithoutTier);
    verify(scoreRepository).save(scoreCaptor.capture());
    assertEquals("Bronze", scoreCaptor.getValue().getTierName());
    assertEquals(TIER_GROUP_IDENTIFIER, scoreCaptor.getValue().getTierGroupIdentifier());
    verify(tierGroupService).resolveScoreTier(any(TierGroupEntity.class), eq(TIER_GROUP_IDENTIFIER), eq(40));
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void computeScoresReassignsDefaultTierWhenReferencedTierGroupDeleted()
      throws IOException, NoSuchAlgorithmException, KeyManagementException {
    ScorecardEntity scorecardWithDeletedTier = getMockScorecardCustomWeights();
    scorecardWithDeletedTier.setTierGroupIdentifier(CUSTOM_TIER_GROUP_ID);
    List<String> scorecardIdentifiers = Collections.emptyList();
    List<String> entityIdentifiers = Collections.singletonList(ENTITY_UID1);
    ScorecardAndChecks scorecardAndChecks =
        ScorecardAndChecks.builder().scorecard(scorecardWithDeletedTier).checks(getMockChecks()).build();
    Response<Object> response = getMockServicesApiResponse();
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    DataPointEntity datapoint1 = getMockDataPoint(DATA_POINT_IDENTIFIER1, false);
    DataPointEntity datapoint2 = getMockDataPoint(DATA_POINT_IDENTIFIER2, true);
    Map<String, Map<String, Object>> data = mockResponseData(true, false);
    when(scorecardService.getAllScorecardAndChecks(ACCOUNT_ID, scorecardIdentifiers))
        .thenReturn(List.of(scorecardAndChecks));
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntities(anyString())).thenReturn(call);
    when(configReader.fetchAllConfigs(ACCOUNT_ID)).thenReturn(null);
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
    when(datapointRepository.findByIdentifierIn(Set.of(DATA_POINT_IDENTIFIER1, DATA_POINT_IDENTIFIER2)))
        .thenReturn(List.of(datapoint1, datapoint2));
    when(dataSourceProviderFactory.getProvider(DATA_SOURCE_IDENTIFIER, false)).thenReturn(dataSourceProvider);
    when(dataSourceProvider.fetchData(eq(ACCOUNT_ID), any(BackstageCatalogComponentEntity.class), anyList(), any()))
        .thenReturn(data);
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(mock(TransactionStatus.class));
    });
    when(idpCommonService.idpV2Enabled(ACCOUNT_ID)).thenReturn(false);
    when(scorecardService.ensureScorecardTierGroupIdentifier(ACCOUNT_ID, scorecardWithDeletedTier))
        .thenAnswer(invocation -> {
          scorecardWithDeletedTier.setTierGroupIdentifier(TIER_GROUP_IDENTIFIER);
          return TIER_GROUP_IDENTIFIER;
        });
    when(tierGroupService.resolveScoreTier(any(TierGroupEntity.class), eq(TIER_GROUP_IDENTIFIER), anyInt()))
        .thenReturn(Optional.of(new ScoreTier().tierName("Bronze").tierGroupIdentifier(TIER_GROUP_IDENTIFIER)));

    scoreComputerService.computeScores(ACCOUNT_ID, scorecardIdentifiers, entityIdentifiers);

    verify(scorecardService, times(2)).ensureScorecardTierGroupIdentifier(ACCOUNT_ID, scorecardWithDeletedTier);
    verify(scoreRepository).save(scoreCaptor.capture());
    assertEquals("Bronze", scoreCaptor.getValue().getTierName());
    assertEquals(TIER_GROUP_IDENTIFIER, scoreCaptor.getValue().getTierGroupIdentifier());
    assertEquals(TIER_GROUP_IDENTIFIER, scorecardWithDeletedTier.getTierGroupIdentifier());
    verify(tierGroupService).resolveScoreTier(any(TierGroupEntity.class), eq(TIER_GROUP_IDENTIFIER), eq(40));
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void computeScoresSavesWithoutTierWhenTierResolutionFails()
      throws IOException, NoSuchAlgorithmException, KeyManagementException {
    List<String> scorecardIdentifiers = Collections.emptyList();
    List<String> entityIdentifiers = Collections.singletonList(ENTITY_UID1);
    ScorecardAndChecks scorecardAndChecks =
        ScorecardAndChecks.builder().scorecard(getMockScorecardCustomWeights()).checks(getMockChecks()).build();
    Response<Object> response = getMockServicesApiResponse();
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    DataPointEntity datapoint1 = getMockDataPoint(DATA_POINT_IDENTIFIER1, false);
    DataPointEntity datapoint2 = getMockDataPoint(DATA_POINT_IDENTIFIER2, true);
    Map<String, Map<String, Object>> data = mockResponseData(true, false);
    List<ScorecardAndChecks> scorecardAndChecksList = new ArrayList<>();
    scorecardAndChecksList.add(scorecardAndChecks);
    when(scorecardService.getAllScorecardAndChecks(ACCOUNT_ID, scorecardIdentifiers))
        .thenReturn(scorecardAndChecksList);
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntities(anyString())).thenReturn(call);
    when(configReader.fetchAllConfigs(ACCOUNT_ID)).thenReturn(null);
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
    when(datapointRepository.findByIdentifierIn(Set.of(DATA_POINT_IDENTIFIER1, DATA_POINT_IDENTIFIER2)))
        .thenReturn(List.of(datapoint1, datapoint2));
    when(dataSourceProviderFactory.getProvider(DATA_SOURCE_IDENTIFIER, false)).thenReturn(dataSourceProvider);
    when(dataSourceProvider.fetchData(eq(ACCOUNT_ID), any(BackstageCatalogComponentEntity.class), anyList(), any()))
        .thenReturn(data);
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(mock(TransactionStatus.class));
    });
    when(idpCommonService.idpV2Enabled(ACCOUNT_ID)).thenReturn(false);
    stubEnsureScorecardTierGroupIdentifier();
    when(tierGroupService.resolveScoreTier(any(TierGroupEntity.class), eq(TIER_GROUP_IDENTIFIER), anyInt()))
        .thenReturn(Optional.empty());

    scoreComputerService.computeScores(ACCOUNT_ID, scorecardIdentifiers, entityIdentifiers);

    verify(scoreRepository).save(scoreCaptor.capture());
    assertNull(scoreCaptor.getValue().getTierName());
    assertNull(scoreCaptor.getValue().getTierGroupIdentifier());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void computeScoresSavesScoreWhenTierEnrichmentThrows()
      throws IOException, NoSuchAlgorithmException, KeyManagementException {
    List<String> scorecardIdentifiers = Collections.emptyList();
    List<String> entityIdentifiers = Collections.singletonList(ENTITY_UID1);
    ScorecardAndChecks scorecardAndChecks =
        ScorecardAndChecks.builder().scorecard(getMockScorecardCustomWeights()).checks(getMockChecks()).build();
    Response<Object> response = getMockServicesApiResponse();
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    DataPointEntity datapoint1 = getMockDataPoint(DATA_POINT_IDENTIFIER1, false);
    DataPointEntity datapoint2 = getMockDataPoint(DATA_POINT_IDENTIFIER2, true);
    Map<String, Map<String, Object>> data = mockResponseData(true, false);
    when(scorecardService.getAllScorecardAndChecks(ACCOUNT_ID, scorecardIdentifiers))
        .thenReturn(List.of(scorecardAndChecks));
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntities(anyString())).thenReturn(call);
    when(configReader.fetchAllConfigs(ACCOUNT_ID)).thenReturn(null);
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
    when(datapointRepository.findByIdentifierIn(Set.of(DATA_POINT_IDENTIFIER1, DATA_POINT_IDENTIFIER2)))
        .thenReturn(List.of(datapoint1, datapoint2));
    when(dataSourceProviderFactory.getProvider(DATA_SOURCE_IDENTIFIER, false)).thenReturn(dataSourceProvider);
    when(dataSourceProvider.fetchData(eq(ACCOUNT_ID), any(BackstageCatalogComponentEntity.class), anyList(), any()))
        .thenReturn(data);
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(mock(TransactionStatus.class));
    });
    when(idpCommonService.idpV2Enabled(ACCOUNT_ID)).thenReturn(false);
    stubEnsureScorecardTierGroupIdentifier();
    when(tierGroupService.resolveScoreTier(any(TierGroupEntity.class), eq(TIER_GROUP_IDENTIFIER), anyInt()))
        .thenThrow(new InvalidRequestException("tier resolution failed"));

    scoreComputerService.computeScores(ACCOUNT_ID, scorecardIdentifiers, entityIdentifiers);

    verify(scoreRepository).save(scoreCaptor.capture());
    assertEquals(40, scoreCaptor.getValue().getScore());
    assertNull(scoreCaptor.getValue().getTierName());
  }

  private Response<Object> getMockServicesApiResponse() {
    List<Map<String, Object>> services = new ArrayList<>();
    for (BackstageCatalogComponentEntity service : getMockServices()) {
      String responseString = gson.toJson(service);
      Map<String, Object> responseMap =
          gson.fromJson(responseString, new TypeToken<Map<String, Object>>() {}.getType());
      services.add(responseMap);
    }
    return Response.success(services);
  }
  private List<BackstageCatalogComponentEntity> getMockServices() {
    BackstageCatalogComponentEntity service1 =
        BackstageCatalogComponentEntity.builder()
            .entityUid(ENTITY_UID1)
            .kind(BackstageCatalogEntityTypes.COMPONENT.kind)
            .relations(Collections.emptySet())
            .metadata(Map.of(MetadataFieldConstants.NAME, IDP_SERVICE_NAME, MetadataFieldConstants.TAGS, List.of(TAG),
                MetadataFieldConstants.HARNESS_DATA, Collections.emptyMap()))
            .spec(BackstageCatalogComponentEntity.Spec.builder()
                      .type(FILTER_TYPE_SERVICE)
                      .owner(OWNER)
                      .lifecycle(LIFECYCLE)
                      .build())
            .build();
    BackstageCatalogComponentEntity service2 =
        BackstageCatalogComponentEntity.builder()
            .entityUid(ENTITY_UID2)
            .kind(BackstageCatalogEntityTypes.COMPONENT.kind)
            .relations(Collections.emptySet())
            .metadata(Map.of(MetadataFieldConstants.NAME, PMS_SERVICE_NAME, MetadataFieldConstants.HARNESS_DATA,
                Collections.emptyMap()))
            .spec(BackstageCatalogComponentEntity.Spec.builder().type(FILTER_TYPE_SERVICE).owner(OWNER).build())
            .build();
    return List.of(service1, service2);
  }
  private List<CheckEntity> getMockChecks() {
    Rule rule1 = new Rule();
    rule1.setIdentifier(RULE_IDENTIFIER1);
    rule1.setDataSourceIdentifier(DATA_SOURCE_IDENTIFIER);
    rule1.setDataPointIdentifier(DATA_POINT_IDENTIFIER1);
    rule1.setOperator(OPERATOR1);
    rule1.setValue(VALUE);
    CheckEntity check1 = CheckEntity.builder()
                             .accountIdentifier(ACCOUNT_ID)
                             .identifier(CHECK_IDENTIFIER1)
                             .name(CHECK_IDENTIFIER1)
                             .ruleStrategy(CheckDetails.RuleStrategyEnum.ALL_OF)
                             .rules(Collections.singletonList(rule1))
                             .build();
    InputValue inputValue = new InputValue();
    inputValue.setKey("key");
    inputValue.value(INPUT_VALUE);
    Rule rule2 = new Rule();
    rule2.setIdentifier(RULE_IDENTIFIER2);
    rule2.setDataSourceIdentifier(DATA_SOURCE_IDENTIFIER);
    rule2.setDataPointIdentifier(DATA_POINT_IDENTIFIER2);
    rule2.setOperator(OPERATOR2);
    rule2.setValue(VALUE);
    rule2.setInputValues(List.of(inputValue));
    CheckEntity check2 = CheckEntity.builder()
                             .accountIdentifier(ACCOUNT_ID)
                             .identifier(CHECK_IDENTIFIER2)
                             .name(CHECK_IDENTIFIER2)
                             .ruleStrategy(CheckDetails.RuleStrategyEnum.ALL_OF)
                             .rules(Collections.singletonList(rule2))
                             .build();
    return List.of(check1, check2);
  }
  private DataPointEntity getMockDataPoint(String identifier, boolean isConditional) {
    return DataPointEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .identifier(identifier)
        .name(identifier)
        .dataSourceIdentifier(DATA_SOURCE_IDENTIFIER)
        .dataSourceLocationIdentifier(DATA_SOURCE_LOCATION_IDENTIFIER)
        .type(DataPointEntity.Type.BOOLEAN)
        .isConditional(isConditional)
        .build();
  }
  private ScorecardEntity getMockScorecardCustomWeights() {
    ScorecardFilter scorecardFilter = new ScorecardFilter();
    scorecardFilter.setKind(BackstageCatalogEntityTypes.COMPONENT.kind);
    scorecardFilter.setType(FILTER_TYPE_SERVICE);
    scorecardFilter.setOwners(Collections.singletonList(OWNER));
    scorecardFilter.setTags(List.of());
    scorecardFilter.setLifecycle(List.of());
    ScorecardEntity.Check check1 =
        ScorecardEntity.Check.builder().identifier(CHECK_IDENTIFIER1).isCustom(false).weightage(2).build();
    ScorecardEntity.Check check2 =
        ScorecardEntity.Check.builder().identifier(CHECK_IDENTIFIER2).isCustom(false).weightage(3).build();
    return ScorecardEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .identifier(SCORECARD_IDENTIFIER1)
        .name(SCORECARD_IDENTIFIER1)
        .tierGroupIdentifier(TIER_GROUP_IDENTIFIER)
        .weightageStrategy(ScorecardDetails.WeightageStrategyEnum.CUSTOM)
        .filter(scorecardFilter)
        .checks(List.of(check1, check2))
        .build();
  }
  private ScorecardEntity getMockScorecardEqualWeights() {
    ScorecardFilter scorecardFilter = new ScorecardFilter();
    scorecardFilter.setKind(BackstageCatalogEntityTypes.COMPONENT.kind);
    scorecardFilter.setType(FILTER_TYPE_SERVICE);
    scorecardFilter.setOwners(Collections.singletonList(OWNER));
    scorecardFilter.setTags(List.of(TAG));
    scorecardFilter.setLifecycle(List.of(LIFECYCLE));
    ScorecardEntity.Check check1 =
        ScorecardEntity.Check.builder().identifier(CHECK_IDENTIFIER1).isCustom(false).weightage(1).build();
    ScorecardEntity.Check check2 =
        ScorecardEntity.Check.builder().identifier(CHECK_IDENTIFIER2).isCustom(false).weightage(1).build();
    return ScorecardEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .identifier(SCORECARD_IDENTIFIER2)
        .name(SCORECARD_IDENTIFIER2)
        .tierGroupIdentifier(TIER_GROUP_IDENTIFIER)
        .weightageStrategy(ScorecardDetails.WeightageStrategyEnum.EQUAL_WEIGHTS)
        .filter(scorecardFilter)
        .checks(List.of(check1, check2))
        .build();
  }
  private void stubEnsureScorecardTierGroupIdentifier() {
    when(scorecardService.ensureScorecardTierGroupIdentifier(eq(ACCOUNT_ID), any(ScorecardEntity.class)))
        .thenAnswer(invocation -> {
          ScorecardEntity scorecard = invocation.getArgument(1);
          String tierGroupIdentifier = scorecard.getTierGroupIdentifier();
          if (tierGroupIdentifier == null || tierGroupIdentifier.trim().isEmpty()) {
            scorecard.setTierGroupIdentifier(TIER_GROUP_IDENTIFIER);
            return TIER_GROUP_IDENTIFIER;
          }
          return tierGroupIdentifier.trim();
        });
  }

  private TierGroupEntity getTierGroupEntity() {
    return TierGroupEntity.builder()
        .identifier(TIER_GROUP_IDENTIFIER)
        .tiers(List.of(TierGroupEntity.Tier.builder().name("Bronze").minScore(0).maxScore(74).colour("#CD7F32").build(),
            TierGroupEntity.Tier.builder().name("Gold").minScore(75).maxScore(100).colour("#FFD700").build()))
        .build();
  }

  private static Answer executeRunnable(ArgumentCaptor<Runnable> runnableCaptor) {
    return invocation -> {
      runnableCaptor.getValue().run();
      return null;
    };
  }
  private Map<String, Map<String, Object>> mockResponseData(boolean value1, boolean value2) {
    return Map.of(DATA_SOURCE_IDENTIFIER,
        Map.of(RULE_IDENTIFIER1, Map.of(DATA_POINT_VALUE_KEY, value1, ERROR_MESSAGE_KEY, "Invalid config"),
            RULE_IDENTIFIER2, Map.of(DATA_POINT_VALUE_KEY, value2, ERROR_MESSAGE_KEY, "Invalid config")));
  }
}