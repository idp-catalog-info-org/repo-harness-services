/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.service;

import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.idp.common.DateUtils.startOfTheDayInMilliseconds;
import static io.harness.rule.OwnerRule.AGNIVA;
import static io.harness.rule.OwnerRule.NITESH_GAHLOT;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
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
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.KindServiceHelper;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.checks.service.CheckService;
import io.harness.idp.scorecard.scorecards.beans.BackstageCatalogEntityFacets;
import io.harness.idp.scorecard.scorecards.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecards.beans.StatsMetadata;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.entity.ScorecardStatsEntity;
import io.harness.idp.scorecard.scorecards.events.ScorecardUpdateEvent;
import io.harness.idp.scorecard.scorecards.repositories.CountAndPercentage;
import io.harness.idp.scorecard.scorecards.repositories.ScorecardIdentifierAndStats;
import io.harness.idp.scorecard.scorecards.repositories.ScorecardRepository;
import io.harness.idp.scorecard.scorecards.repositories.ScorecardStatsRepository;
import io.harness.idp.scorecard.scorecards.service.ScorecardServiceImpl;
import io.harness.idp.scorecard.tiergroups.entity.TierGroupEntity;
import io.harness.idp.scorecard.tiergroups.service.TierGroupService;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.EntityFiltersResponse;
import io.harness.spec.server.idp.v1.model.Scorecard;
import io.harness.spec.server.idp.v1.model.ScorecardChecks;
import io.harness.spec.server.idp.v1.model.ScorecardChecksDetails;
import io.harness.spec.server.idp.v1.model.ScorecardDetails;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsRequest;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsResponse;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.idp.v1.model.ScorecardStatsResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.IDP)
public class ScorecardServiceImplTest extends CategoryTest {
  private ScorecardServiceImpl scorecardServiceImpl;
  @Mock ScorecardRepository scorecardRepository;
  @Mock ScorecardStatsRepository scorecardStatsRepository;
  @Mock CheckService checkService;
  @Mock BackstageResourceClient backstageResourceClient;
  @Mock AccountClient accountClient;
  @Mock Call<Object> call;
  @Mock ObjectMapper objectMapper;
  @Mock CatalogServiceHelper catalogServiceHelper;
  @Mock KindServiceHelper kindServiceHelper;
  @Mock TierGroupService tierGroupService;
  @Mock IdpCommonService idpCommonService;

  @Mock TransactionTemplate transactionTemplate;

  @Mock OutboxService outboxService;
  private static final String ACCOUNT_ID = "123";
  private static final String SCORECARD_ID = "service_maturity";
  private static final String SCORECARD_NAME = "Service Maturity";
  private static final String GITHUB_CHECK_NAME = "Github Checks";
  private static final String GITHUB_CHECK_ID = "github_checks";
  private static final String CATALOG_CHECK_NAME = "Catalog Checks";
  private static final String CATALOG_CHECK_ID = "catalog_checks";
  private static final String SAMPLE_CHECK_ID = "sample_check";
  private static final List<String> SCORECARD_IDENTIFIERS = Arrays.asList("scorecard1", "scorecard2");
  private static final List<String> SCORECARD_IDENTIFIERS_BACKSTAGE =
      new ArrayList<>(List.of("Component/Domain/Group"));
  private static final String TEST_CHECK_IDENTIFIER = "test-check-identifier";
  private static final boolean TEST_CHECK_IS_CUSTOM = false;
  private static final double TEST_CHECK_WRIGHT = 1.0;
  private static final String IDP_SERVICE_ENTITY_NAME = "idp-service";
  private static final String OLD_ENTITY_IDENTIFIER = "old-entity";
  private static final String NEW_ENTITY_IDENTIFIER = "new-entity";

  private static final String TIER_GROUP_ID = "default_tiers";
  private static final String CUSTOM_TIER_GROUP_ID = "custom_tiers";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    scorecardServiceImpl = new ScorecardServiceImpl(scorecardRepository, scorecardStatsRepository, checkService,
        backstageResourceClient, transactionTemplate, outboxService, catalogServiceHelper, kindServiceHelper,
        tierGroupService, idpCommonService);
    when(idpCommonService.idpScorecardTiersEnabled(ACCOUNT_ID)).thenReturn(true);
    org.mockito.Mockito.doNothing().when(tierGroupService).validateTierGroupReference(any(), any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetAllScorecardsAndChecksDetails() {
    ScorecardEntity scorecardEntity = getScorecardEntity();
    when(scorecardRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(List.of(scorecardEntity));
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(getCheckEntities());
    List<Scorecard> scorecards = scorecardServiceImpl.getAllScorecardsAndChecksDetails(ACCOUNT_ID);
    assertEquals(1, scorecards.size());
    assertEquals(1, scorecards.get(0).getChecksMissing().size());
    assertEquals(SAMPLE_CHECK_ID, scorecards.get(0).getChecksMissing().get(0));
    assertEquals(3, (int) scorecards.get(0).getComponents());
    assertEquals(2, scorecards.get(0).getTierAnalytics().size());
    assertEquals("Silver", scorecards.get(0).getTierAnalytics().get(0).getTierName());
    assertEquals(33.0, scorecards.get(0).getTierAnalytics().get(0).getPercentage());
    assertEquals("Gold", scorecards.get(0).getTierAnalytics().get(1).getTierName());
    assertEquals(67.0, scorecards.get(0).getTierAnalytics().get(1).getPercentage());
    assertThat(scorecards.get(0).getTierGroupIdentifier()).isEqualTo(TIER_GROUP_ID);
    verify(scorecardStatsRepository, never()).computeScoresPercentageByScorecard(any(), any(), anyLong());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testGetAllScorecardsUsesLegacyCountsWhenTierAnalyticsDisabled() {
    when(idpCommonService.idpScorecardTiersEnabled(ACCOUNT_ID)).thenReturn(false);
    when(scorecardRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(List.of(getScorecardEntity()));
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(getCheckEntities());
    when(scorecardStatsRepository.findLastUpdatedByScorecardIdentifiers(ACCOUNT_ID, List.of(SCORECARD_ID)))
        .thenReturn(List.of(getScorecardIdAndStats()));
    when(scorecardStatsRepository.computeScoresPercentageByScorecard(any(), eq(SCORECARD_ID), anyLong()))
        .thenReturn(CountAndPercentage.builder().count(5).percentage(0.8).build());

    List<Scorecard> scorecards = scorecardServiceImpl.getAllScorecardsAndChecksDetails(ACCOUNT_ID);

    assertEquals(5, (int) scorecards.get(0).getComponents());
    assertEquals(80.0, scorecards.get(0).getPercentage());
    assertThat(scorecards.get(0).getTierAnalytics()).isNull();
    assertThat(scorecards.get(0).getTierGroupIdentifier()).isNull();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetAllScorecardAndChecks() {
    ScorecardEntity scorecardEntity = getScorecardEntity();
    when(scorecardRepository.findByAccountIdentifierAndPublished(ACCOUNT_ID, true))
        .thenReturn(List.of(scorecardEntity));
    when(scorecardRepository.findByAccountIdentifierAndIdentifierIn(ACCOUNT_ID, List.of(SCORECARD_ID)))
        .thenReturn(List.of(scorecardEntity));
    when(checkService.getActiveChecks(any(), any())).thenReturn(getCheckEntities());
    List<ScorecardAndChecks> scorecardDetailsList =
        scorecardServiceImpl.getAllScorecardAndChecks(ACCOUNT_ID, List.of(SCORECARD_ID));
    assertEquals(1, scorecardDetailsList.size());
    assertEquals(2, scorecardDetailsList.get(0).getChecks().size());
    scorecardDetailsList = scorecardServiceImpl.getAllScorecardAndChecks(ACCOUNT_ID, new ArrayList<>());
    assertEquals(1, scorecardDetailsList.size());
    assertEquals(2, scorecardDetailsList.get(0).getChecks().size());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveScorecardWithNoChecks() {
    ScorecardDetailsRequest request = getScorecardDetailsRequest(true, true);
    scorecardServiceImpl.saveScorecard(request, ACCOUNT_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveScorecardWithInvalidCheck() {
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, true);
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(getCheckEntities());
    scorecardServiceImpl.saveScorecard(request, ACCOUNT_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveScorecardWithDeletedCheck() {
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, true);
    List<CheckEntity> checkEntities = new ArrayList<>(getCheckEntities());
    checkEntities.add(CheckEntity.builder()
                          .accountIdentifier(ACCOUNT_ID)
                          .identifier(SAMPLE_CHECK_ID)
                          .isCustom(true)
                          .isDeleted(true)
                          .build());
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(checkEntities);
    scorecardServiceImpl.saveScorecard(request, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveScorecard() {
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, true);
    List<CheckEntity> checkEntities = new ArrayList<>(getCheckEntities());
    checkEntities.add(
        CheckEntity.builder().accountIdentifier(ACCOUNT_ID).identifier(SAMPLE_CHECK_ID).isCustom(true).build());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(checkEntities);
    ScorecardFilter scorecardFilter = new ScorecardFilter();
    scorecardFilter.setKind("component");
    when(scorecardRepository.save(any()))
        .thenReturn(ScorecardEntity.builder()
                        .filter(scorecardFilter)
                        .checks(Collections.singletonList(getTestCheck()))
                        .build());
    doNothing().when(kindServiceHelper).validateKindIfExist(ACCOUNT_ID, "component");
    assertThatCode(() -> scorecardServiceImpl.saveScorecard(request, ACCOUNT_ID)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testSaveScorecardWithoutTierWhenFeatureEnabledRejectsMissingAndBlankTier() {
    stubValidChecks();

    for (String tierGroupIdentifier : Arrays.asList(null, "  ")) {
      ScorecardDetailsRequest request = getScorecardDetailsRequest(false, true);
      request.getScorecard().setTierGroupIdentifier(tierGroupIdentifier);

      assertThatThrownBy(() -> scorecardServiceImpl.saveScorecard(request, ACCOUNT_ID))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessage("Tier group identifier is required for scorecard");
    }

    verify(tierGroupService, never()).createDefaultTierGroupIfAbsent(ACCOUNT_ID);
    verify(tierGroupService, never()).validateTierGroupReference(any(), any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testSavePublishedScorecardWithoutTierWhenFeatureDisabledLeavesTierUnassigned() {
    when(idpCommonService.idpScorecardTiersEnabled(ACCOUNT_ID)).thenReturn(false);
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, true);
    request.getScorecard().setTierGroupIdentifier(null);
    request.getScorecard().getFilter().setScopes(List.of("account.*"));
    stubValidChecks();
    stubTransactionExecution();
    when(scorecardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    scorecardServiceImpl.saveScorecard(request, ACCOUNT_ID);

    ArgumentCaptor<ScorecardEntity> entityCaptor = ArgumentCaptor.forClass(ScorecardEntity.class);
    verify(scorecardRepository).save(entityCaptor.capture());
    assertThat(entityCaptor.getValue().getTierGroupIdentifier()).isNull();
    assertThat(entityCaptor.getValue().isPublished()).isTrue();
    verify(tierGroupService, never()).createDefaultTierGroupIfAbsent(ACCOUNT_ID);
    verify(tierGroupService, never()).validateTierGroupReference(any(), any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testSaveScorecardWithCustomTierWhenFeatureDisabledPreservesCustomTier() {
    when(idpCommonService.idpScorecardTiersEnabled(ACCOUNT_ID)).thenReturn(false);
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, true);
    request.getScorecard().setTierGroupIdentifier("  " + CUSTOM_TIER_GROUP_ID + "  ");
    request.getScorecard().getFilter().setScopes(List.of("account.*"));
    stubValidChecks();
    stubTransactionExecution();
    when(scorecardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    scorecardServiceImpl.saveScorecard(request, ACCOUNT_ID);

    ArgumentCaptor<ScorecardEntity> entityCaptor = ArgumentCaptor.forClass(ScorecardEntity.class);
    verify(scorecardRepository).save(entityCaptor.capture());
    assertThat(entityCaptor.getValue().getTierGroupIdentifier()).isEqualTo(CUSTOM_TIER_GROUP_ID);
    verify(tierGroupService, never()).createDefaultTierGroupIfAbsent(ACCOUNT_ID);
    verify(tierGroupService).validateTierGroupReference(ACCOUNT_ID, CUSTOM_TIER_GROUP_ID);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testSaveScorecardWithTierWhenFeatureEnabledNormalizesIdentifier() {
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, true);
    request.getScorecard().setTierGroupIdentifier("  " + CUSTOM_TIER_GROUP_ID + "  ");
    request.getScorecard().getFilter().setScopes(List.of("account.*"));
    stubValidChecks();
    stubTransactionExecution();
    when(scorecardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    scorecardServiceImpl.saveScorecard(request, ACCOUNT_ID);

    ArgumentCaptor<ScorecardEntity> entityCaptor = ArgumentCaptor.forClass(ScorecardEntity.class);
    verify(scorecardRepository).save(entityCaptor.capture());
    assertThat(entityCaptor.getValue().getTierGroupIdentifier()).isEqualTo(CUSTOM_TIER_GROUP_ID);
    verify(tierGroupService).validateTierGroupReference(ACCOUNT_ID, CUSTOM_TIER_GROUP_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateScorecard() {
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, false);
    List<CheckEntity> checkEntities = new ArrayList<>(getCheckEntities());
    checkEntities.add(
        CheckEntity.builder().accountIdentifier(ACCOUNT_ID).identifier(SAMPLE_CHECK_ID).isCustom(true).build());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(any(), any()))
        .thenReturn(ScorecardEntity.builder().checks(Collections.singletonList(getTestCheck())).build());
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(checkEntities);
    when(scorecardRepository.update(any()))
        .thenReturn(ScorecardEntity.builder().checks(Collections.singletonList(getTestCheck())).build());
    assertThatCode(() -> scorecardServiceImpl.updateScorecard(request, ACCOUNT_ID)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testUpdateScorecardWithoutTierWhenFeatureDisabledPreservesExistingCustomTier() {
    when(idpCommonService.idpScorecardTiersEnabled(ACCOUNT_ID)).thenReturn(false);
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, false);
    request.getScorecard().setTierGroupIdentifier(null);
    ScorecardEntity existingScorecard = getScorecardEntityWithTier("  " + CUSTOM_TIER_GROUP_ID + "  ");
    stubValidChecks();
    stubTransactionExecution();
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, SCORECARD_ID))
        .thenReturn(existingScorecard);
    when(scorecardRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

    scorecardServiceImpl.updateScorecard(request, ACCOUNT_ID);

    ArgumentCaptor<ScorecardEntity> entityCaptor = ArgumentCaptor.forClass(ScorecardEntity.class);
    verify(scorecardRepository).update(entityCaptor.capture());
    assertThat(entityCaptor.getValue().getTierGroupIdentifier()).isEqualTo(CUSTOM_TIER_GROUP_ID);
    verify(tierGroupService, never()).createDefaultTierGroupIfAbsent(ACCOUNT_ID);
    verify(tierGroupService, never()).validateTierGroupReference(any(), any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testUpdateScorecardWithoutTierResolvesExistingTierOnceBeforeRetry() {
    when(idpCommonService.idpScorecardTiersEnabled(ACCOUNT_ID)).thenReturn(false);
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, false);
    request.getScorecard().setTierGroupIdentifier(null);
    ScorecardEntity firstRead = getScorecardEntityWithTier("first_tiers");
    ScorecardEntity latestRead = getScorecardEntityWithTier("latest_tiers");
    stubValidChecks();
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, SCORECARD_ID))
        .thenReturn(firstRead)
        .thenReturn(latestRead);
    when(transactionTemplate.execute(any()))
        .thenThrow(new OptimisticLockingFailureException("conflict"))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(scorecardRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

    scorecardServiceImpl.updateScorecard(request, ACCOUNT_ID);

    verify(tierGroupService, never()).createDefaultTierGroupIfAbsent(ACCOUNT_ID);
    verify(tierGroupService, never()).validateTierGroupReference(any(), any());
    ArgumentCaptor<ScorecardEntity> entityCaptor = ArgumentCaptor.forClass(ScorecardEntity.class);
    verify(scorecardRepository, times(1)).update(entityCaptor.capture());
    assertThat(entityCaptor.getValue().getTierGroupIdentifier()).isEqualTo("first_tiers");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testUpdateScorecardWithoutTierWhenFeatureDisabledLeavesStoredNullTierUnassigned() {
    when(idpCommonService.idpScorecardTiersEnabled(ACCOUNT_ID)).thenReturn(false);
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, false);
    request.getScorecard().setTierGroupIdentifier(null);
    ScorecardEntity existingScorecard = getScorecardEntityWithTier(null);
    stubValidChecks();
    stubTransactionExecution();
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, SCORECARD_ID))
        .thenReturn(existingScorecard);
    when(scorecardRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

    scorecardServiceImpl.updateScorecard(request, ACCOUNT_ID);

    ArgumentCaptor<ScorecardEntity> entityCaptor = ArgumentCaptor.forClass(ScorecardEntity.class);
    verify(scorecardRepository).update(entityCaptor.capture());
    assertThat(entityCaptor.getValue().getTierGroupIdentifier()).isNull();
    verify(tierGroupService, never()).createDefaultTierGroupIfAbsent(ACCOUNT_ID);
    verify(tierGroupService, never()).validateTierGroupReference(any(), any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testUpdateScorecardWithoutTierWhenFeatureEnabledRejectsRequest() {
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, false);
    request.getScorecard().setTierGroupIdentifier(null);
    stubValidChecks();
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, SCORECARD_ID))
        .thenReturn(getScorecardEntityWithTier(CUSTOM_TIER_GROUP_ID));

    assertThatThrownBy(() -> scorecardServiceImpl.updateScorecard(request, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Tier group identifier is required for scorecard");

    verify(tierGroupService, never()).createDefaultTierGroupIfAbsent(ACCOUNT_ID);
    verify(tierGroupService, never()).validateTierGroupReference(any(), any());
    verify(scorecardRepository, never()).update(any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testSaveScorecardWithNonExistentTierGroupRejects() {
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, true);
    request.getScorecard().setTierGroupIdentifier(CUSTOM_TIER_GROUP_ID);
    request.getScorecard().getFilter().setScopes(List.of("account.*"));
    stubValidChecks();
    org.mockito.Mockito.doThrow(new InvalidRequestException("Could not find tier group " + CUSTOM_TIER_GROUP_ID))
        .when(tierGroupService)
        .validateTierGroupReference(ACCOUNT_ID, CUSTOM_TIER_GROUP_ID);

    assertThatThrownBy(() -> scorecardServiceImpl.saveScorecard(request, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Could not find tier group");

    verify(scorecardRepository, never()).save(any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testUpdateScorecardWithExplicitNewTierWhenFeatureDisabled() {
    when(idpCommonService.idpScorecardTiersEnabled(ACCOUNT_ID)).thenReturn(false);
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, false);
    request.getScorecard().setTierGroupIdentifier(CUSTOM_TIER_GROUP_ID);
    ScorecardEntity existingScorecard = getScorecardEntityWithTier(TIER_GROUP_ID);
    stubValidChecks();
    stubTransactionExecution();
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, SCORECARD_ID))
        .thenReturn(existingScorecard);
    when(scorecardRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

    scorecardServiceImpl.updateScorecard(request, ACCOUNT_ID);

    ArgumentCaptor<ScorecardEntity> entityCaptor = ArgumentCaptor.forClass(ScorecardEntity.class);
    verify(scorecardRepository).update(entityCaptor.capture());
    assertThat(entityCaptor.getValue().getTierGroupIdentifier()).isEqualTo(CUSTOM_TIER_GROUP_ID);
    verify(tierGroupService).validateTierGroupReference(ACCOUNT_ID, CUSTOM_TIER_GROUP_ID);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testUpdateScorecardChangesTierGroupWhenFeatureEnabled() {
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, false);
    request.getScorecard().setTierGroupIdentifier(CUSTOM_TIER_GROUP_ID);
    ScorecardEntity existingScorecard = getScorecardEntityWithTier(TIER_GROUP_ID);
    stubValidChecks();
    stubTransactionExecution();
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, SCORECARD_ID))
        .thenReturn(existingScorecard);
    when(scorecardRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

    scorecardServiceImpl.updateScorecard(request, ACCOUNT_ID);

    ArgumentCaptor<ScorecardEntity> entityCaptor = ArgumentCaptor.forClass(ScorecardEntity.class);
    verify(scorecardRepository).update(entityCaptor.capture());
    assertThat(entityCaptor.getValue().getTierGroupIdentifier()).isEqualTo(CUSTOM_TIER_GROUP_ID);
    verify(tierGroupService).validateTierGroupReference(ACCOUNT_ID, CUSTOM_TIER_GROUP_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetScorecardDetails() {
    ScorecardEntity scorecardEntity = getScorecardEntity();
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, SCORECARD_ID))
        .thenReturn(scorecardEntity);
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(getCheckEntities());
    ScorecardDetailsResponse response = scorecardServiceImpl.getScorecardDetails(ACCOUNT_ID, SCORECARD_ID);
    assertEquals(SCORECARD_ID, response.getScorecard().getIdentifier());
    assertEquals(1, response.getScorecard().getChecksMissing().size());
    assertEquals(SAMPLE_CHECK_ID, response.getScorecard().getChecksMissing().get(0));
    assertEquals(3, (int) response.getScorecard().getComponents());
    assertEquals(2, response.getScorecard().getTierAnalytics().size());
    assertEquals(33.0, response.getScorecard().getTierAnalytics().get(0).getPercentage());
    assertEquals(67.0, response.getScorecard().getTierAnalytics().get(1).getPercentage());
    verify(scorecardStatsRepository, never()).computeScoresPercentageByScorecard(any(), any(), anyLong());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testGetScorecardDetailsUsesLegacyCountsWhenTierAnalyticsDisabled() {
    when(idpCommonService.idpScorecardTiersEnabled(ACCOUNT_ID)).thenReturn(false);
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, SCORECARD_ID))
        .thenReturn(getScorecardEntity());
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(getCheckEntities());
    when(scorecardStatsRepository.findLastUpdatedByScorecardIdentifiers(ACCOUNT_ID, List.of(SCORECARD_ID)))
        .thenReturn(List.of(getScorecardIdAndStats()));
    when(scorecardStatsRepository.computeScoresPercentageByScorecard(any(), eq(SCORECARD_ID), anyLong()))
        .thenReturn(CountAndPercentage.builder().count(5).percentage(0.8).build());

    ScorecardDetailsResponse response = scorecardServiceImpl.getScorecardDetails(ACCOUNT_ID, SCORECARD_ID);

    assertEquals(5, (int) response.getScorecard().getComponents());
    assertEquals(80.0, response.getScorecard().getPercentage());
    assertThat(response.getScorecard().getTierAnalytics()).isNull();
    assertThat(response.getScorecard().getTierGroupIdentifier()).isNull();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetScorecardDetailsThrowsException() {
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(any(), any())).thenReturn(null);
    scorecardServiceImpl.getScorecardDetails(ACCOUNT_ID, SCORECARD_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetScorecardStats() {
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, SCORECARD_ID))
        .thenReturn(getScorecardEntity());
    when(scorecardStatsRepository.findLastUpdatedByScorecardIdentifiers(ACCOUNT_ID, List.of(SCORECARD_ID)))
        .thenReturn(List.of(getScorecardIdAndStats()));
    when(scorecardStatsRepository.findByAccountIdentifierAndScorecardIdentifierAndLastUpdatedAtGreaterThan(ACCOUNT_ID,
             SCORECARD_ID, startOfTheDayInMilliseconds(getScorecardStatsEntities().get(0).getLastUpdatedAt())))
        .thenReturn(getScorecardStatsEntities());
    ScorecardStatsResponse response = scorecardServiceImpl.getScorecardStats(ACCOUNT_ID, SCORECARD_ID);
    assertEquals(SCORECARD_NAME, response.getName());
    assertEquals(1, response.getStats().size());
    assertEquals(IDP_SERVICE_ENTITY_NAME, response.getStats().get(0).getName());
    assertEquals(75, (int) response.getStats().get(0).getScore());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetScorecardStatsThrowsException() {
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, SCORECARD_ID)).thenReturn(null);
    scorecardServiceImpl.getScorecardStats(ACCOUNT_ID, SCORECARD_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetScorecardIdentifiers() {
    when(scorecardRepository.findByCheckIdentifierAndIsCustom(any(), any(), any()))
        .thenReturn(List.of(getScorecardEntity()));
    List<String> scorecardIds = scorecardServiceImpl.getScorecardIdentifiers(ACCOUNT_ID, GITHUB_CHECK_ID, Boolean.TRUE);
    assertEquals(1, scorecardIds.size());
    assertEquals(SCORECARD_ID, scorecardIds.get(0));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteScorecard() {
    List<CheckEntity> checkEntities = new ArrayList<>(getCheckEntities());
    checkEntities.add(
        CheckEntity.builder().accountIdentifier(ACCOUNT_ID).identifier(SAMPLE_CHECK_ID).isCustom(true).build());
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(checkEntities);
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(any(), any()))
        .thenReturn(ScorecardEntity.builder().checks(Collections.singletonList(getTestCheck())).build());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    DeleteResult deleteResult = DeleteResult.acknowledged(1);
    when(scorecardRepository.delete(ACCOUNT_ID, SCORECARD_ID)).thenReturn(deleteResult);
    assertThatCode(() -> scorecardServiceImpl.deleteScorecard(ACCOUNT_ID, SCORECARD_ID)).doesNotThrowAnyException();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteScorecardThrowsException() {
    List<CheckEntity> checkEntities = new ArrayList<>(getCheckEntities());
    checkEntities.add(
        CheckEntity.builder().accountIdentifier(ACCOUNT_ID).identifier(SAMPLE_CHECK_ID).isCustom(true).build());
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(checkEntities);
    ScorecardEntity.Check check =
        ScorecardEntity.Check.builder().weightage(1.0).isCustom(false).identifier("test").build();
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(any(), any()))
        .thenReturn(ScorecardEntity.builder().checks(Collections.singletonList(check)).build());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    DeleteResult deleteResult = DeleteResult.acknowledged(0);
    when(scorecardRepository.delete(ACCOUNT_ID, SCORECARD_ID)).thenReturn(deleteResult);
    scorecardServiceImpl.deleteScorecard(ACCOUNT_ID, SCORECARD_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetAllEntityFacets() throws IOException {
    String data = "{\n"
        + "    \"facets\": {\n"
        + "        \"spec.type\": [\n"
        + "            {\n"
        + "                \"value\": \"library\",\n"
        + "                \"count\": 2\n"
        + "            },\n"
        + "            {\n"
        + "                \"value\": \"service\",\n"
        + "                \"count\": 33\n"
        + "            }\n"
        + "        ],\n"
        + "        \"relations.ownedBy\": [\n"
        + "            {\n"
        + "                \"value\": \"group:default/ccmplayacc\",\n"
        + "                \"count\": 1\n"
        + "            },\n"
        + "            {\n"
        + "                \"value\": \"group:default/cncf\",\n"
        + "                \"count\": 1\n"
        + "            }\n"
        + "        ],\n"
        + "        \"metadata.tags\": [\n"
        + "            {\n"
        + "                \"value\": \"data\",\n"
        + "                \"count\": 4\n"
        + "            },\n"
        + "            {\n"
        + "                \"value\": \"django\",\n"
        + "                \"count\": 1\n"
        + "            }\n"
        + "        ],\n"
        + "        \"spec.lifecycle\": [\n"
        + "            {\n"
        + "                \"value\": \"experimental\",\n"
        + "                \"count\": 20\n"
        + "            },\n"
        + "            {\n"
        + "                \"value\": \"prod\",\n"
        + "                \"count\": 2\n"
        + "            }\n"
        + "        ]\n"
        + "    }\n"
        + "}";
    Response<Object> response =
        Response.success(200, ResponseBody.create("Content", MediaType.parse("application/json")));
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntityFacets(any())).thenReturn(call);
    on(scorecardServiceImpl).set("mapper", objectMapper);
    doReturn(GsonUtils.convertJsonStringToObject(data, BackstageCatalogEntityFacets.class))
        .when(objectMapper)
        .convertValue(any(), eq(BackstageCatalogEntityFacets.class));
    List<EntityFiltersResponse> entityFiltersResponses =
        scorecardServiceImpl.getAllEntityFacets(ACCOUNT_ID, "component");
    for (EntityFiltersResponse entityFiltersResponse : entityFiltersResponses) {
      if (entityFiltersResponse.getFilter().equals("type")) {
        assertEquals(List.of("library", "service"), entityFiltersResponse.getValues());
      } else if (entityFiltersResponse.getFilter().equals("tags")) {
        assertEquals(List.of("data", "django"), entityFiltersResponse.getValues());
      }
    }
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testgetScorecardFilters() {
    ScorecardEntity scorecardEntity = getScorecardEntity();
    List<ScorecardEntity> scorecardEntities = Arrays.asList(scorecardEntity);
    when(scorecardRepository.findByAccountIdentifierAndIdentifierIn(ACCOUNT_ID, SCORECARD_IDENTIFIERS))
        .thenReturn(scorecardEntities);
    List<ScorecardFilter> filters = scorecardServiceImpl.getScorecardFilters(ACCOUNT_ID, SCORECARD_IDENTIFIERS);
    List<ScorecardFilter> expectedFilters = scorecardEntities.stream()
                                                .filter(ScorecardEntity::isPublished)
                                                .map(ScorecardEntity::getFilter)
                                                .collect(Collectors.toList());
    assertEquals(expectedFilters, filters);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testmigrateEntityIdentifier() {
    Map<String, String> entityIdentifiersMap = Map.of(OLD_ENTITY_IDENTIFIER, NEW_ENTITY_IDENTIFIER);
    List<String> entityIdentifiers = new ArrayList<>(List.of(OLD_ENTITY_IDENTIFIER));
    when(scorecardStatsRepository.findUniqueEntityIdentifiers(ACCOUNT_ID)).thenReturn(entityIdentifiers);
    assertTrue("Entity identifier map should contain OLD_ENTITY_IDENTIFIER",
        entityIdentifiersMap.containsKey(OLD_ENTITY_IDENTIFIER));
    UpdateResult updateResult = mock(UpdateResult.class);
    when(updateResult.getModifiedCount()).thenReturn(1L);
    when(scorecardStatsRepository.updateEntityIdentifier(ACCOUNT_ID, OLD_ENTITY_IDENTIFIER, NEW_ENTITY_IDENTIFIER))
        .thenReturn(updateResult);
    scorecardServiceImpl.migrateEntityIdentifier(entityIdentifiersMap, ACCOUNT_ID);
    verify(scorecardStatsRepository).updateEntityIdentifier(ACCOUNT_ID, OLD_ENTITY_IDENTIFIER, NEW_ENTITY_IDENTIFIER);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testModifyEntityIdentifier() {
    List<String> entityIdentifiers = Arrays.asList(OLD_ENTITY_IDENTIFIER, NEW_ENTITY_IDENTIFIER);
    when(scorecardStatsRepository.findUniqueEntityIdentifiers(ACCOUNT_ID)).thenReturn(entityIdentifiers);
    UpdateResult updateResult = mock(UpdateResult.class);
    when(updateResult.getModifiedCount()).thenReturn(1L);
    when(scorecardStatsRepository.updateEntityIdentifier(ACCOUNT_ID, OLD_ENTITY_IDENTIFIER, NEW_ENTITY_IDENTIFIER))
        .thenReturn(updateResult);
    scorecardServiceImpl.modifyEntityIdentifier(ACCOUNT_ID);
    verify(scorecardStatsRepository).findUniqueEntityIdentifiers(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void ensureScorecardTierGroupIdentifierReturnsExistingTierGroup() {
    ScorecardEntity scorecard = getScorecardEntityWithTier("  " + CUSTOM_TIER_GROUP_ID + "  ");
    when(tierGroupService.getActiveTierGroup(ACCOUNT_ID, CUSTOM_TIER_GROUP_ID))
        .thenReturn(TierGroupEntity.builder().identifier(CUSTOM_TIER_GROUP_ID).build());

    String tierGroupIdentifier = scorecardServiceImpl.ensureScorecardTierGroupIdentifier(ACCOUNT_ID, scorecard);

    assertThat(tierGroupIdentifier).isEqualTo(CUSTOM_TIER_GROUP_ID);
    verify(tierGroupService).getActiveTierGroup(ACCOUNT_ID, CUSTOM_TIER_GROUP_ID);
    verify(tierGroupService, never()).createDefaultTierGroupIfAbsent(ACCOUNT_ID);
    verify(scorecardRepository, never()).update(any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void ensureScorecardTierGroupIdentifierAssignsDefaultWhenMissing() {
    ScorecardEntity scorecard = getScorecardEntityWithTier(null);
    when(scorecardRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
    stubValidChecks();
    stubTransactionExecution();

    String tierGroupIdentifier = scorecardServiceImpl.ensureScorecardTierGroupIdentifier(ACCOUNT_ID, scorecard);

    assertThat(tierGroupIdentifier).isEqualTo(TIER_GROUP_ID);
    assertThat(scorecard.getTierGroupIdentifier()).isEqualTo(TIER_GROUP_ID);
    verify(tierGroupService, never()).getActiveTierGroup(any(), any());
    verify(tierGroupService).createDefaultTierGroupIfAbsent(ACCOUNT_ID);
    ArgumentCaptor<ScorecardEntity> updatedScorecardCaptor = ArgumentCaptor.forClass(ScorecardEntity.class);
    verify(scorecardRepository).update(updatedScorecardCaptor.capture());
    assertThat(updatedScorecardCaptor.getValue().getTierGroupIdentifier()).isEqualTo(TIER_GROUP_ID);
    ArgumentCaptor<ScorecardUpdateEvent> eventCaptor = ArgumentCaptor.forClass(ScorecardUpdateEvent.class);
    verify(outboxService).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getOldScorecardDetailsResponse().getScorecard().getTierGroupIdentifier())
        .isNull();
    assertThat(eventCaptor.getValue().getNewScorecardDetailsResponse().getScorecard().getTierGroupIdentifier())
        .isEqualTo(TIER_GROUP_ID);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void ensureScorecardTierGroupIdentifierAssignsDefaultWhenReferencedTierGroupMissing() {
    ScorecardEntity scorecard = getScorecardEntityWithTier(CUSTOM_TIER_GROUP_ID);
    when(tierGroupService.getActiveTierGroup(ACCOUNT_ID, CUSTOM_TIER_GROUP_ID)).thenReturn(null);
    when(scorecardRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
    stubValidChecks();
    stubTransactionExecution();

    String tierGroupIdentifier = scorecardServiceImpl.ensureScorecardTierGroupIdentifier(ACCOUNT_ID, scorecard);

    assertThat(tierGroupIdentifier).isEqualTo(TIER_GROUP_ID);
    assertThat(scorecard.getTierGroupIdentifier()).isEqualTo(TIER_GROUP_ID);
    verify(tierGroupService).getActiveTierGroup(ACCOUNT_ID, CUSTOM_TIER_GROUP_ID);
    verify(tierGroupService).createDefaultTierGroupIfAbsent(ACCOUNT_ID);
    ArgumentCaptor<ScorecardEntity> updatedScorecardCaptor = ArgumentCaptor.forClass(ScorecardEntity.class);
    verify(scorecardRepository).update(updatedScorecardCaptor.capture());
    assertThat(updatedScorecardCaptor.getValue().getTierGroupIdentifier()).isEqualTo(TIER_GROUP_ID);
    ArgumentCaptor<ScorecardUpdateEvent> eventCaptor = ArgumentCaptor.forClass(ScorecardUpdateEvent.class);
    verify(outboxService).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getOldScorecardDetailsResponse().getScorecard().getTierGroupIdentifier())
        .isEqualTo(CUSTOM_TIER_GROUP_ID);
    assertThat(eventCaptor.getValue().getNewScorecardDetailsResponse().getScorecard().getTierGroupIdentifier())
        .isEqualTo(TIER_GROUP_ID);
  }

  private ScorecardEntity getScorecardEntity() {
    ScorecardEntity.Check check1 = ScorecardEntity.Check.builder().identifier(GITHUB_CHECK_ID).isCustom(true).build();
    ScorecardEntity.Check check2 = ScorecardEntity.Check.builder().identifier(CATALOG_CHECK_ID).isCustom(false).build();
    ScorecardEntity.Check check3 = ScorecardEntity.Check.builder().identifier(SAMPLE_CHECK_ID).isCustom(true).build();
    return ScorecardEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .identifier(SCORECARD_ID)
        .name(SCORECARD_NAME)
        .checks(List.of(check1, check2, check3))
        .filter(new ScorecardFilter().kind("component").type("service"))
        .published(true)
        .tierGroupIdentifier(TIER_GROUP_ID)
        .componentCount(3)
        .tierComponentCounts(List.of(ScorecardEntity.TierComponentCount.builder()
                                         .tierName("Silver")
                                         .minScore(50)
                                         .maxScore(74)
                                         .tierColour("#C0C0C0")
                                         .componentCount(1)
                                         .build(),
            ScorecardEntity.TierComponentCount.builder()
                .tierName("Gold")
                .minScore(75)
                .maxScore(100)
                .tierColour("#00FF00")
                .componentCount(2)
                .build()))
        .build();
  }

  private ScorecardEntity getScorecardEntityWithTier(String tierGroupIdentifier) {
    ScorecardEntity scorecardEntity = getScorecardEntity();
    scorecardEntity.setTierGroupIdentifier(tierGroupIdentifier);
    return scorecardEntity;
  }

  private void stubValidChecks() {
    List<CheckEntity> checkEntities = new ArrayList<>(getCheckEntities());
    checkEntities.add(
        CheckEntity.builder().accountIdentifier(ACCOUNT_ID).identifier(SAMPLE_CHECK_ID).isCustom(true).build());
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(checkEntities);
  }

  private void stubTransactionExecution() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
  }

  private List<CheckEntity> getCheckEntities() {
    CheckEntity entity1 = CheckEntity.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .identifier(GITHUB_CHECK_ID)
                              .name(GITHUB_CHECK_NAME)
                              .isCustom(true)
                              .build();
    CheckEntity entity2 = CheckEntity.builder()
                              .accountIdentifier(GLOBAL_ACCOUNT_ID)
                              .identifier(CATALOG_CHECK_ID)
                              .name(CATALOG_CHECK_NAME)
                              .isCustom(false)
                              .build();
    return List.of(entity1, entity2);
  }

  private ScorecardDetailsRequest getScorecardDetailsRequest(boolean isEmptyChecks, boolean isEqualWeights) {
    ScorecardDetailsRequest request = new ScorecardDetailsRequest();
    ScorecardDetails scorecardDetails = new ScorecardDetails();
    scorecardDetails.setName(SCORECARD_NAME);
    scorecardDetails.setIdentifier(SCORECARD_ID);
    scorecardDetails.setPublished(true);
    scorecardDetails.setTierGroupIdentifier(TIER_GROUP_ID);
    scorecardDetails.setWeightageStrategy(isEqualWeights ? ScorecardDetails.WeightageStrategyEnum.EQUAL_WEIGHTS
                                                         : ScorecardDetails.WeightageStrategyEnum.CUSTOM);
    ScorecardFilter scorecardFilter = new ScorecardFilter();
    scorecardFilter.setKind("component");
    scorecardDetails.setFilter(scorecardFilter);
    request.setScorecard(scorecardDetails);
    if (isEmptyChecks) {
      request.setChecks(new ArrayList<>());
    } else {
      ScorecardChecks scorecardChecks1 = new ScorecardChecksDetails();
      scorecardChecks1.setIdentifier(GITHUB_CHECK_ID);
      scorecardChecks1.setCustom(true);
      scorecardChecks1.setWeightage(2.0);
      ScorecardChecks scorecardChecks2 = new ScorecardChecksDetails();
      scorecardChecks2.setIdentifier(CATALOG_CHECK_ID);
      scorecardChecks2.setCustom(false);
      scorecardChecks2.setWeightage(4.0);
      ScorecardChecks scorecardChecks3 = new ScorecardChecksDetails();
      scorecardChecks3.setIdentifier(SAMPLE_CHECK_ID);
      scorecardChecks3.setCustom(true);
      scorecardChecks3.setWeightage(5.0);
      request.setChecks(List.of(scorecardChecks1, scorecardChecks2, scorecardChecks3));
    }
    return request;
  }

  private ScorecardEntity.Check getTestCheck() {
    return ScorecardEntity.Check.builder()
        .weightage(TEST_CHECK_WRIGHT)
        .isCustom(TEST_CHECK_IS_CUSTOM)
        .identifier(TEST_CHECK_IDENTIFIER)
        .build();
  }
  private List<ScorecardStatsEntity> getScorecardStatsEntities() {
    ScorecardStatsEntity scorecardStatsEntity = ScorecardStatsEntity.builder()
                                                    .metadata(StatsMetadata.builder()
                                                                  .name(IDP_SERVICE_ENTITY_NAME)
                                                                  .owner("team-a")
                                                                  .system("Unknown")
                                                                  .kind("component")
                                                                  .type("service")
                                                                  .build())
                                                    .score(75)
                                                    .build();
    scorecardStatsEntity.setLastUpdatedAt(1716098830867L);
    return List.of(scorecardStatsEntity);
  }

  private ScorecardIdentifierAndStats getScorecardIdAndStats() {
    return ScorecardIdentifierAndStats.builder()
        .scorecardStatsEntity(getScorecardStatsEntities().get(0))
        .scorecardIdentifier(SCORECARD_ID)
        .build();
  }
}
