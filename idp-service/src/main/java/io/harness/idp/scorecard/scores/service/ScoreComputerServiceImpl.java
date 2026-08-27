/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.expression.common.ExpressionMode.RETURN_NULL_IF_UNRESOLVED;
import static io.harness.idp.backstage.utils.BackstageUtils.getEntityUniqueId;
import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.MISSING_DATA;
import static io.harness.idp.common.Constants.SPACE_SEPARATOR;
import static io.harness.idp.common.JacksonUtils.convert;
import static io.harness.idp.scorecard.checks.mappers.CheckDetailsMapper.constructExpressionFromRules;
import static io.harness.idp.scorecard.checks.mappers.CheckDetailsMapper.getDisplayExpression;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.clients.BackstageResourceClient;
import io.harness.clients.IdpAgentClient;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.KindServiceHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.metrics.IdpIteratorMetricRecorder;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.checks.mappers.CheckDetailsMapper;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasources.DataSourceProvider;
import io.harness.idp.scorecard.datasources.providers.DataSourceProviderFactory;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;
import io.harness.idp.scorecard.expression.IdpExpressionEvaluator;
import io.harness.idp.scorecard.scorecards.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.events.ScorecardCheckFailureEvent;
import io.harness.idp.scorecard.scorecards.events.ScorecardRecalibrateEvent;
import io.harness.idp.scorecard.scorecards.repositories.ScorecardRepository;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.idp.scorecard.scores.logging.ScoreComputationLogContext;
import io.harness.idp.scorecard.scores.repositories.ScoreEntityByEntityIdentifier;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;
import io.harness.idp.scorecard.tiergroups.entity.TierGroupEntity;
import io.harness.idp.scorecard.tiergroups.service.TierGroupService;
import io.harness.logging.AutoLogContext;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.CGRestUtils;
import io.harness.spec.server.idp.v1.model.CheckDetails;
import io.harness.spec.server.idp.v1.model.CheckStatus;
import io.harness.spec.server.idp.v1.model.EvaluationData;
import io.harness.spec.server.idp.v1.model.Rule;
import io.harness.spec.server.idp.v1.model.ScorecardDetails;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsResponse;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateInfo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.data.domain.Page;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class ScoreComputerServiceImpl implements ScoreComputerService {
  private static final String CATALOG_API_SUFFIX = "%s/idp/api/catalog/entities?%s&limit=%s";
  private static final String SCORECARD_COUNT_REFRESH = "ScorecardCountRefresh";
  ExecutorService iteratorExecutorService;
  ExecutorService userExecutorService;
  private String backstageEntitiesFetchLimit;
  private TransactionTemplate transactionTemplate;
  ScorecardService scorecardService;
  ScoreService scoreService;
  BackstageResourceClient backstageResourceClient;
  IdpAgentClient idpAgentClient;
  DataSourceProviderFactory dataSourceProviderFactory;
  ScoreRepository scoreRepository;
  ScorecardRepository scorecardRepository;
  ConfigReader configReader;
  private final OutboxService outboxService;
  AsyncScoreComputationService asyncScoreComputationService;
  IdpCommonService idpCommonService;
  private final AccountClient accountClient;
  private final CatalogEntityRepository catalogEntityRepository;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  static final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final Gson gson = new Gson();
  CatalogServiceHelper catalogServiceHelper;
  KindServiceHelper kindServiceHelper;
  TierGroupService tierGroupService;
  IdpIteratorMetricRecorder idpIteratorMetricRecorder;
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;

  private record TierGroupSnapshot(String tierGroupIdentifier, TierGroupEntity tierGroup) {}

  @Inject
  public ScoreComputerServiceImpl(@Named("ScoreComputer") ExecutorService iteratorExecutorService,
      AccountClient accountClient, AsyncScoreComputationService asyncScoreComputationService, ConfigReader configReader,
      OutboxService outboxService, ScoreRepository scoreRepository, ScorecardRepository scorecardRepository,
      DataSourceProviderFactory dataSourceProviderFactory, ScorecardService scorecardService, ScoreService scoreService,
      BackstageResourceClient backstageResourceClient, IdpAgentClient idpAgentClient,
      @Named("backstageEntitiesFetchLimit") String backstageEntitiesFetchLimit,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate,
      @Named("UserScoreComputer") ExecutorService userExecutorService, CatalogEntityRepository catalogEntityRepository,
      IdpCommonService idpCommonService, CatalogServiceHelper catalogServiceHelper, KindServiceHelper kindServiceHelper,
      TierGroupService tierGroupService, IdpIteratorMetricRecorder idpIteratorMetricRecorder) {
    this.iteratorExecutorService = iteratorExecutorService;
    this.accountClient = accountClient;
    this.asyncScoreComputationService = asyncScoreComputationService;
    this.configReader = configReader;
    this.outboxService = outboxService;
    this.scoreRepository = scoreRepository;
    this.scorecardRepository = scorecardRepository;
    this.dataSourceProviderFactory = dataSourceProviderFactory;
    this.scorecardService = scorecardService;
    this.scoreService = scoreService;
    this.backstageResourceClient = backstageResourceClient;
    this.idpAgentClient = idpAgentClient;
    this.backstageEntitiesFetchLimit = backstageEntitiesFetchLimit;
    this.transactionTemplate = transactionTemplate;
    this.userExecutorService = userExecutorService;
    this.catalogEntityRepository = catalogEntityRepository;
    this.idpCommonService = idpCommonService;
    this.catalogServiceHelper = catalogServiceHelper;
    this.kindServiceHelper = kindServiceHelper;
    this.tierGroupService = tierGroupService;
    this.idpIteratorMetricRecorder = idpIteratorMetricRecorder;
  }

  @Override
  public void computeScores(
      String accountIdentifier, List<String> scorecardIdentifiers, List<String> entityIdentifiers) {
    List<ScorecardAndChecks> scorecardsAndChecks =
        new ArrayList<>(scorecardService.getAllScorecardAndChecks(accountIdentifier, scorecardIdentifiers));
    // Filter scorecards with onDemand disabled for daily score computation
    if (isEmpty(scorecardIdentifiers)) {
      scorecardsAndChecks.removeIf(scorecardAndChecks -> scorecardAndChecks.getScorecard().isOnDemand());
    }

    String configs = configReader.fetchAllConfigs(accountIdentifier);
    boolean asyncScoreComputationEnabled = CGRestUtils.getResponse(
        accountClient.isFeatureFlagEnabled(FeatureName.IDP_ASYNC_SCORE_COMPUTATION.name(), accountIdentifier));
    boolean idpV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    boolean tierAnalyticsEnabled = idpCommonService.idpScorecardTiersEnabled(accountIdentifier);
    log.info(
        "IDP_ASYNC_SCORE_COMPUTATION FF enabled: {} for account {}", asyncScoreComputationEnabled, accountIdentifier);
    log.info("IDP_SCORECARD_TIERS FF enabled: {} for account {}", tierAnalyticsEnabled, accountIdentifier);

    boolean isUseLocalGitConnectorForScoreComputationEnabled =
        CGRestUtils.getResponse(accountClient.isFeatureFlagEnabled(
            FeatureName.USE_LOCAL_GIT_CONNECTOR_FOR_SCORE_COMPUTATION.name(), accountIdentifier));

    log.info("USE_LOCAL_GIT_CONNECTOR_FOR_SCORE_COMPUTATION is - {} for account - {}",
        isUseLocalGitConnectorForScoreComputationEnabled, accountIdentifier);

    boolean fullScoreComputation = scorecardIdentifiers.isEmpty() && entityIdentifiers.isEmpty();
    for (ScorecardAndChecks scorecardAndChecks : scorecardsAndChecks) {
      long recalculationStartedAt = System.currentTimeMillis();
      Set<?> ens;
      if (idpV2Enabled) {
        ens = getCatalogEntitiesForScorecardsAndEntityIdentifiers(
            accountIdentifier, Collections.singletonList(scorecardAndChecks), entityIdentifiers);
      } else {
        ens = getBackstageEntitiesForScorecardsAndEntityIdentifiers(
            accountIdentifier, Collections.singletonList(scorecardAndChecks), entityIdentifiers);
      }
      Set<String> computedEntityIdentifiers = ens.stream().map(CatalogUtils::getEntityUUId).collect(Collectors.toSet());
      Supplier<Set<String>> countRefreshEntityIdentifiers = ()
          -> fullScoreComputation ? computedEntityIdentifiers
                                  : getMatchingEntityIdentifiers(accountIdentifier, scorecardAndChecks, idpV2Enabled);
      if (ens.isEmpty()) {
        log.warn("Account {} has no backstage entities matching the scorecard filters", accountIdentifier);
        if (tierAnalyticsEnabled) {
          refreshScorecardCounts(
              accountIdentifier, scorecardAndChecks, recalculationStartedAt, countRefreshEntityIdentifiers);
        }
        continue;
      }

      Map<String, List<DataFetchDTO>> dataToFetchByProvider =
          getProviderDataToFetch(Collections.singletonList(scorecardAndChecks));

      Optional<TierGroupSnapshot> tierGroupSnapshot =
          buildTierGroupSnapshot(accountIdentifier, scorecardAndChecks.getScorecard());

      CountDownLatch latch = new CountDownLatch(ens.size());
      AtomicInteger remainingTasks = new AtomicInteger(ens.size());
      Runnable onTaskComplete = () -> {
        if (remainingTasks.decrementAndGet() == 0 && tierAnalyticsEnabled) {
          refreshScorecardCounts(
              accountIdentifier, scorecardAndChecks, recalculationStartedAt, countRefreshEntityIdentifiers);
        }
      };
      log.info("Score computer running for account: {}, scorecard: {}", accountIdentifier,
          scorecardAndChecks.getScorecard().getIdentifier());
      for (Object entity : ens) {
        if (fullScoreComputation) {
          iteratorExecutorService.submit(
              ()
                  -> runTask(latch, configs, dataToFetchByProvider, accountIdentifier, entity,
                      Collections.singletonList(scorecardAndChecks), isUseLocalGitConnectorForScoreComputationEnabled,
                      true, onTaskComplete, tierGroupSnapshot));
        } else {
          userExecutorService.submit(
              ()
                  -> runTask(latch, configs, dataToFetchByProvider, accountIdentifier, entity,
                      Collections.singletonList(scorecardAndChecks), isUseLocalGitConnectorForScoreComputationEnabled,
                      false, onTaskComplete, tierGroupSnapshot));

          if (!asyncScoreComputationEnabled) {
            Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
              ScorecardEntity scorecard = scorecardAndChecks.getScorecard();
              outboxService.save(new ScorecardRecalibrateEvent(
                  accountIdentifier, scorecard.getIdentifier(), scorecard.getIdentifier()));
              return true;
            }));
          }
        }
      }

      if (asyncScoreComputationEnabled || (entityIdentifiers != null && !entityIdentifiers.isEmpty())) {
        try {
          if (!latch.await(30, TimeUnit.SECONDS)) {
            log.warn("Timeout waiting for threads to complete.");
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          log.warn("Interrupted while waiting for threads.");
        }
      }
    }
  }

  @Override
  public ScorecardRecalibrateInfo computeScoresAsync(
      String accountIdentifier, String scorecardIdentifier, String entityIdentifier) {
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      ScorecardRecalibrateInfo scorecardRecalibrateInfo =
          asyncScoreComputationService.getRecalibrateInfo(accountIdentifier, scorecardIdentifier, entityIdentifier);
      if (scorecardRecalibrateInfo != null) {
        log.info("Score computation is already in progress for scorecard {}, entity {} and account {}",
            scorecardIdentifier, entityIdentifier, accountIdentifier);
        return scorecardRecalibrateInfo;
      }
      ScorecardDetailsResponse scorecardDetailsResponse =
          scorecardService.getScorecardDetails(accountIdentifier, scorecardIdentifier);
      ScorecardDetails scorecard = scorecardDetailsResponse.getScorecard();
      outboxService.save(
          new ScorecardRecalibrateEvent(accountIdentifier, scorecard.getIdentifier(), scorecard.getName()));
      return asyncScoreComputationService.logScoreComputationRequestAndPublishEvent(
          accountIdentifier, scorecardIdentifier, entityIdentifier);
    }));
  }

  @Override
  public Set<? extends BackstageCatalogEntity> getBackstageEntitiesForScorecardsAndEntityIdentifiers(
      String accountIdentifier, List<ScorecardAndChecks> scorecardsAndChecks, List<String> entityIdentifiers) {
    if (scorecardsAndChecks.isEmpty()) {
      log.info("No scorecards configured for account: {}", accountIdentifier);
      return new HashSet<>();
    }
    List<ScorecardFilter> filters = getAllFilters(scorecardsAndChecks);
    return getAllEntities(accountIdentifier, entityIdentifiers, filters);
  }

  @Override
  public Set<? extends CatalogEntity> getCatalogEntitiesForScorecardsAndEntityIdentifiers(
      String accountIdentifier, List<ScorecardAndChecks> scorecardsAndChecks, List<String> entityIdentifiers) {
    if (scorecardsAndChecks.isEmpty()) {
      log.info("No scorecards configured for account: {}", accountIdentifier);
      return new HashSet<>();
    }
    List<ScorecardFilter> filters = getAllFilters(scorecardsAndChecks);
    return getAllEntitiesForIDPCatalogs(accountIdentifier, entityIdentifiers, filters);
  }

  @Override
  public Set<BackstageCatalogEntity> getAllEntities(
      String accountIdentifier, List<String> entityIdentifiers, List<ScorecardFilter> filters) {
    Set<BackstageCatalogEntity> allEntities = new HashSet<>();

    for (ScorecardFilter filter : filters) {
      StringBuilder filterStringBuilder = new StringBuilder("filter=kind=").append(filter.getKind().toLowerCase());
      if (StringUtils.isNotBlank(filter.getType()) && !filter.getType().equalsIgnoreCase("all")) {
        filterStringBuilder.append(",spec.type=").append(filter.getType().toLowerCase());
      }

      for (String owner : filter.getOwners()) {
        filterStringBuilder.append(",relations.ownedBy=").append(owner);
      }

      for (String lifecycle : filter.getLifecycle()) {
        filterStringBuilder.append(",spec.lifecycle=").append(lifecycle);
      }

      for (String tag : filter.getTags()) {
        filterStringBuilder.append(",metadata.tags=").append(tag);
      }

      try {
        String url =
            String.format(CATALOG_API_SUFFIX, accountIdentifier, filterStringBuilder, backstageEntitiesFetchLimit);
        log.info("Making backstage API request: {}", url);
        Object entitiesResponse = getGeneralResponse(backstageResourceClient.getCatalogEntities(url));

        List<Map<String, Object>> backstageEntities =
            objectMapper.convertValue(entitiesResponse, new TypeReference<List<Map<String, Object>>>() {});

        for (Map<String, Object> entity : backstageEntities) {
          CommonUtils.normalizeSystemField(entity);
        }

        List<BackstageCatalogEntity> entities = convert(mapper, backstageEntities, BackstageCatalogEntity.class);
        filterEntitiesByTags(entities, filter.getTags());
        if (entityIdentifiers == null || entityIdentifiers.isEmpty()) {
          allEntities.addAll(entities);
        } else {
          allEntities.addAll(
              entities.stream()
                  .filter(entity
                      -> entityIdentifiers.stream().anyMatch(
                          entityIdentifier -> entityIdentifier.equalsIgnoreCase(getEntityUniqueId(entity))))
                  .toList());
        }
      } catch (Exception e) {
        log.error(
            "Error while fetch catalog details for account = {}, entityIdentifiers = {}, filters = {}, error = {}",
            accountIdentifier, entityIdentifiers, filters, e.getMessage(), e);
        throw new UnexpectedException("Error while fetch catalog details", e);
      }
    }
    return allEntities;
  }

  @Override
  public Set<CatalogEntity> getAllEntitiesForIDPCatalogs(
      String accountIdentifier, List<String> entityIdentifiers, List<ScorecardFilter> filters) {
    Set<CatalogEntity> catalogEntities = new HashSet<>();
    String entityRefs = !isEmpty(entityIdentifiers)
        ? entityIdentifiers.stream().map(CatalogUtils::getEntityRefFromUid).collect(Collectors.joining(","))
        : null;
    for (ScorecardFilter filter : filters) {
      String type = null;
      StringBuilder ownersFilter = new StringBuilder();
      StringBuilder lifecyclesFilter = new StringBuilder();
      StringBuilder tagsFilter = new StringBuilder();
      String kind = filter.getKind().toLowerCase();
      StringBuilder scopes = new StringBuilder();
      if (kind.equals("template")) {
        kind = "workflow";
      }
      try {
        kindServiceHelper.validateKindIfExist(accountIdentifier, kind);
      } catch (InvalidRequestException e) {
        log.warn("{} is not supported", kind);
        continue;
      }

      if (StringUtils.isNotBlank(filter.getType()) && !filter.getType().equalsIgnoreCase("all")) {
        type = filter.getType().toLowerCase();
      }

      for (String owner : filter.getOwners()) {
        ownersFilter.append(owner).append(",");
      }

      for (String lifecycle : filter.getLifecycle()) {
        lifecyclesFilter.append(lifecycle).append(",");
      }

      for (String tag : filter.getTags()) {
        tagsFilter.append(tag).append(",");
      }

      List<String> scopesList =
          isEmpty(filter.getScopes()) ? Arrays.asList(catalogServiceHelper.getAllScopes()) : filter.getScopes();

      for (String scope : scopesList) {
        scopes.append(scope).append(",");
      }

      Page<CatalogEntity> catalogEntitiesPaged;
      int page = 0;
      do {
        catalogEntitiesPaged = catalogEntityRepository.getEntities(accountIdentifier,
            catalogServiceHelper
                .getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, scopes.toString(), entityRefs)
                .getLeft(),
            page, -1, null, null, null, entityRefs, kind, type, ownersFilter.toString(), lifecyclesFilter.toString(),
            tagsFilter.toString(), null, null);
        if (!isEmpty(catalogEntitiesPaged) && !isEmpty(catalogEntitiesPaged.getContent())) {
          catalogEntities.addAll(catalogEntitiesPaged.getContent());
        }
        page++;
      } while (!isEmpty(catalogEntitiesPaged) && catalogEntitiesPaged.getTotalPages() > page);
    }

    return catalogEntities;
  }

  private void runTask(CountDownLatch latch, String configs, Map<String, List<DataFetchDTO>> dataToFetchByProvider,
      String accountIdentifier, Object entity, List<ScorecardAndChecks> scorecardsAndChecks,
      boolean isUseLocalGitConnectorForScoreComputationEnabled, boolean generateCheckFailureSummaryAsync,
      Runnable onTaskComplete, Optional<TierGroupSnapshot> tierGroupSnapshot) {
    try {
      Type mapType = new TypeToken<Map<String, List<DataFetchDTO>>>() {}.getType();
      Map<String, List<DataFetchDTO>> deepCopyDataToFetchByProvider =
          gson.fromJson(gson.toJson(dataToFetchByProvider), mapType);
      Map<String, Map<String, Object>> data = fetch(accountIdentifier, entity, deepCopyDataToFetchByProvider, configs,
          isUseLocalGitConnectorForScoreComputationEnabled);
      compute(
          accountIdentifier, entity, scorecardsAndChecks, data, generateCheckFailureSummaryAsync, tierGroupSnapshot);
    } catch (Exception e) {
      log.error("Could not fetch data and compute score for account: {}, entity: {}", accountIdentifier,
          CatalogUtils.getEntityUUId(entity), e);
    } finally {
      try {
        onTaskComplete.run();
      } finally {
        latch.countDown();
      }
    }
  }

  private Set<String> getMatchingEntityIdentifiers(
      String accountIdentifier, ScorecardAndChecks scorecardAndChecks, boolean idpV2Enabled) {
    Set<?> matchingEntities = idpV2Enabled
        ? getCatalogEntitiesForScorecardsAndEntityIdentifiers(
              accountIdentifier, Collections.singletonList(scorecardAndChecks), Collections.emptyList())
        : getBackstageEntitiesForScorecardsAndEntityIdentifiers(
              accountIdentifier, Collections.singletonList(scorecardAndChecks), Collections.emptyList());
    return matchingEntities.stream().map(CatalogUtils::getEntityUUId).collect(Collectors.toSet());
  }

  private void refreshScorecardCounts(String accountIdentifier, ScorecardAndChecks scorecardAndChecks,
      long recalculationStartedAt, Supplier<Set<String>> matchingEntityIdentifiersSupplier) {
    ScorecardEntity scorecard = scorecardAndChecks.getScorecard();
    try {
      Set<String> matchingEntityIdentifiers = matchingEntityIdentifiersSupplier.get();

      List<ScoreEntity> latestScores = matchingEntityIdentifiers.isEmpty()
          ? Collections.emptyList()
          : scoreRepository.getLatestScorePerEntityForScorecard(accountIdentifier, scorecard.getIdentifier())
                .stream()
                .filter(scoreByEntity -> matchingEntityIdentifiers.contains(scoreByEntity.getEntityIdentifier()))
                .map(ScoreEntityByEntityIdentifier::getScoreEntity)
                .filter(Objects::nonNull)
                .toList();

      String tierGroupIdentifier = scorecardService.ensureScorecardTierGroupIdentifier(accountIdentifier, scorecard);
      TierGroupEntity tierGroup = tierGroupService.getActiveTierGroup(accountIdentifier, tierGroupIdentifier);
      if (tierGroup == null || isEmpty(tierGroup.getTiers())) {
        throw new InvalidRequestException(String.format("No tiers found for tier group [%s]", tierGroupIdentifier));
      }
      List<ScorecardEntity.TierComponentCount> tierComponentCounts =
          tierGroup.getTiers()
              .stream()
              .sorted(Comparator.comparingInt(TierGroupEntity.Tier::getMinScore))
              .map(tier
                  -> ScorecardEntity.TierComponentCount.builder()
                         .tierName(tier.getName())
                         .minScore(tier.getMinScore())
                         .maxScore(tier.getMaxScore())
                         .tierColour(tier.getColour())
                         .componentCount((int) latestScores.stream()
                                             .filter(score
                                                 -> score.getScore() >= tier.getMinScore()
                                                     && score.getScore() <= tier.getMaxScore())
                                             .count())
                         .build())
              .toList();
      long latestScoreTimestamp =
          latestScores.stream().mapToLong(ScoreEntity::getLastComputedTimestamp).max().orElse(recalculationStartedAt);
      long scoreCountsComputedAt = Math.max(recalculationStartedAt, latestScoreTimestamp);

      scorecardRepository.updateScoreCounts(accountIdentifier, scorecard.getIdentifier(), latestScores.size(),
          tierComponentCounts, scoreCountsComputedAt);
      idpIteratorMetricRecorder.recordSuccess(SCORECARD_COUNT_REFRESH, accountIdentifier);
    } catch (Exception e) {
      log.error("Could not refresh component counts for account: {}, scorecard: {}", accountIdentifier,
          scorecard.getIdentifier(), e);
      idpIteratorMetricRecorder.recordFailure(SCORECARD_COUNT_REFRESH, accountIdentifier);
    }
  }

  private Optional<TierGroupSnapshot> buildTierGroupSnapshot(String accountIdentifier, ScorecardEntity scorecard) {
    try {
      String tierGroupIdentifier = scorecardService.ensureScorecardTierGroupIdentifier(accountIdentifier, scorecard);
      TierGroupEntity tierGroup = tierGroupService.getActiveTierGroup(accountIdentifier, tierGroupIdentifier);
      if (tierGroup == null) {
        return Optional.empty();
      }
      return Optional.of(new TierGroupSnapshot(tierGroupIdentifier, tierGroup));
    } catch (Exception e) {
      log.warn("Could not build tier group snapshot for account: {}, scorecard: {}", accountIdentifier,
          scorecard.getIdentifier(), e);
      return Optional.empty();
    }
  }

  private void applyTierEnrichment(ScoreEntity.ScoreEntityBuilder scoreBuilder,
      Optional<TierGroupSnapshot> tierGroupSnapshot, int score, String accountIdentifier, String scorecardIdentifier,
      String entityIdentifier) {
    try {
      tierGroupSnapshot.ifPresent(snapshot
          -> tierGroupService.resolveScoreTier(snapshot.tierGroup(), snapshot.tierGroupIdentifier(), score)
                 .ifPresent(resolvedTier -> {
                   scoreBuilder.tierName(resolvedTier.getTierName());
                   scoreBuilder.tierGroupIdentifier(resolvedTier.getTierGroupIdentifier());
                   scoreBuilder.tierDescription(resolvedTier.getTierDescription());
                   scoreBuilder.tierIcon(resolvedTier.getTierIcon());
                   scoreBuilder.tierColour(resolvedTier.getTierColour());
                 }));
    } catch (Exception e) {
      log.warn("Best-effort tier enrichment failed for account: {}, scorecard: {}, entity: {}", accountIdentifier,
          scorecardIdentifier, entityIdentifier, e);
    }
  }

  private List<ScorecardFilter> getAllFilters(List<ScorecardAndChecks> scorecardsAndChecks) {
    return scorecardsAndChecks.stream()
        .map(scorecardAndChecks -> scorecardAndChecks.getScorecard().getFilter())
        .collect(Collectors.toList());
  }

  private Map<String, Map<String, Object>> fetch(String accountIdentifier, Object entity,
      Map<String, List<DataFetchDTO>> providerDataPoints, String configs,
      boolean isUseLocalGitConnectorForScoreComputationEnabled) {
    try (AutoLogContext ignore1 = ScoreComputationLogContext.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .threadName(Thread.currentThread().getName())
                                      .build(AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      log.info("Fetching data from provider for account: {}, entity: {}", accountIdentifier,
          CatalogUtils.getEntityUUId(entity));

      Map<String, Map<String, Object>> aggregatedData = new HashMap<>();
      providerDataPoints.forEach((k, v) -> {
        DataSourceProvider provider =
            dataSourceProviderFactory.getProvider(k, isUseLocalGitConnectorForScoreComputationEnabled);
        try {
          Map<String, Map<String, Object>> data = provider.fetchData(accountIdentifier, entity, v, configs);
          if (data != null) {
            aggregatedData.putAll(data);
          }
        } catch (Exception e) {
          log.warn("Error fetching data from {} provider for account: {}, entity: {}", provider.getIdentifier(),
              accountIdentifier, CatalogUtils.getEntityUUId(entity), e);
        }
      });
      return aggregatedData;
    }
  }

  private void compute(String accountIdentifier, Object entity, List<ScorecardAndChecks> scorecardsAndChecks,
      Map<String, Map<String, Object>> data, boolean generateCheckFailureSummaryAsync,
      Optional<TierGroupSnapshot> tierGroupSnapshot) {
    IdpExpressionEvaluator evaluator = new IdpExpressionEvaluator(data);

    Map<String, Set<ScopeInfo>> scopeInfosForScopesUniques = new HashMap<>();
    for (ScorecardAndChecks scorecardAndChecks : scorecardsAndChecks) {
      ScorecardEntity scorecard = scorecardAndChecks.getScorecard();
      boolean atLeastOneCheckFailed = false;
      try (AutoLogContext ignore1 = ScoreComputationLogContext.builder()
                                        .accountIdentifier(accountIdentifier)
                                        .scorecardIdentifier(scorecard.getIdentifier())
                                        .threadName(Thread.currentThread().getName())
                                        .build(AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
        boolean isFilterMatching;
        if (entity instanceof BackstageCatalogEntity) {
          isFilterMatching = isFilterMatchingWithAnEntity(scorecard.getFilter(), (BackstageCatalogEntity) entity);
        } else {
          Pair<Boolean, Map<String, Set<ScopeInfo>>> isFilterMatchingWithCatalogEntityAndScopeInfosForScope =
              isFilterMatchingWithCatalogEntity(
                  scorecard.getFilter(), (CatalogEntity) entity, scopeInfosForScopesUniques);
          isFilterMatching = isFilterMatchingWithCatalogEntityAndScopeInfosForScope.getLeft();
          scopeInfosForScopesUniques = isFilterMatchingWithCatalogEntityAndScopeInfosForScope.getRight();
        }
        if (!isFilterMatching) {
          log.info("Not computing score as the account: {}, entity {} does not match the scorecard filters",
              accountIdentifier, CatalogUtils.getEntityUUId(entity));
          continue;
        }
        log.info("Computing score for account: {}, entity: {}", accountIdentifier, CatalogUtils.getEntityUUId(entity));
        ScoreEntity.ScoreEntityBuilder scoreBuilder = ScoreEntity.builder()
                                                          .scorecardIdentifier(scorecard.getIdentifier())
                                                          .accountIdentifier(accountIdentifier)
                                                          .entityIdentifier(CatalogUtils.getEntityUUId(entity));

        int totalScore = 0;
        int totalPossibleScore = 0;
        List<CheckStatus> checkStatuses = new ArrayList<>();
        List<CheckEntity> checks = scorecardAndChecks.getChecks();

        Map<String, ScorecardEntity.Check> scorecardCheckByIdentifier = scorecard.getChecks().stream().collect(
            Collectors.toMap(ScorecardEntity.Check::getIdentifier, Function.identity()));

        for (CheckEntity check : checks) {
          CheckStatus checkStatus = new CheckStatus();
          checkStatus.setIdentifier(check.getIdentifier());
          checkStatus.setName(check.getName());
          checkStatus.setCustom(check.isCustom());
          checkStatus.setCheckDescription(check.getDescription());

          Triple<CheckStatus.StatusEnum, String, List<EvaluationData>> statusAndMessage =
              getCheckStatusFailureReasonAndEvaluationData(evaluator, check);
          List<EvaluationData> allRulesEvaluationData = statusAndMessage.getRight();
          StringBuilder finalReason = new StringBuilder();
          if (statusAndMessage.getMiddle() != null) {
            String[] reasonsOfRules = statusAndMessage.getMiddle().split(";");
            int sizeOfEvaluationData = allRulesEvaluationData.size();
            int sizeOfReasonsOfRules = reasonsOfRules.length;
            for (int i = 0; i < sizeOfReasonsOfRules; i++) {
              if (i < sizeOfEvaluationData) {
                String reasonOfRule = reasonsOfRules[i].trim();
                allRulesEvaluationData.get(i).setReason(reasonOfRule);
                if (reasonsOfRules[i] != null && !reasonsOfRules[i].trim().isEmpty()) {
                  finalReason.append(reasonOfRule).append(";").append(SPACE_SEPARATOR);
                }
              }
            }
          }

          checkStatus.setStatus(statusAndMessage.getLeft());
          if (check.getRuleStrategy().equals(CheckDetails.RuleStrategyEnum.ADVANCED)) {
            Map<String, EvaluationData> uniqueMap = new HashMap<>();
            for (EvaluationData evaluationDataOfRule : allRulesEvaluationData) {
              if (evaluationDataOfRule.getRuleExpression() != null) {
                uniqueMap.put(evaluationDataOfRule.getRuleExpression(), evaluationDataOfRule);
              }
            }
            checkStatus.setEvaluationData(new ArrayList<>(uniqueMap.values()));
          } else {
            checkStatus.setEvaluationData(allRulesEvaluationData);
          }
          checkStatus.setRuleStrategy(check.getRuleStrategy().toString());
          checkStatus.setExpression(check.getExpression());

          checkStatus.setReason(finalReason.toString());
          log.info("Account: {}, Check {}, Status : {}, Reason: {}", accountIdentifier, check.getIdentifier(),
              checkStatus.getStatus(), statusAndMessage.getMiddle());

          double weightage = scorecardCheckByIdentifier.get(check.getIdentifier()).getWeightage();
          totalPossibleScore += weightage;
          totalScore += (checkStatus.getStatus().equals(CheckStatus.StatusEnum.PASS) ? 1 : 0) * weightage;
          checkStatus.setWeight((int) weightage);
          if (statusAndMessage.getLeft().equals(CheckStatus.StatusEnum.FAIL)) {
            atLeastOneCheckFailed = true;
          }
          checkStatuses.add(checkStatus);
        }

        int score = totalPossibleScore == 0 ? 0 : Math.round((float) totalScore / totalPossibleScore * 100);
        long scorecardComputationTimestamp = System.currentTimeMillis();
        scoreBuilder.checkStatus(checkStatuses);
        scoreBuilder.score(score);
        scoreBuilder.lastComputedTimestamp(scorecardComputationTimestamp);
        applyTierEnrichment(scoreBuilder, tierGroupSnapshot, score, accountIdentifier, scorecard.getIdentifier(),
            CatalogUtils.getEntityUUId(entity));
        scoreRepository.save(scoreBuilder.build());
        log.info("Score computed for account: {}, entity {} with score: {}", accountIdentifier,
            CatalogUtils.getEntityUUId(entity), score);
        if (atLeastOneCheckFailed) {
          if (generateCheckFailureSummaryAsync) {
            outboxService.save(new ScorecardCheckFailureEvent(accountIdentifier, scorecard.getIdentifier(),
                scorecard.getIdentifier(), CatalogUtils.getEntityUUId(entity), scorecardComputationTimestamp));
          } else {
            scoreService.generateFailureSummaryForFailedChecksInScore(accountIdentifier, scorecard.getIdentifier(),
                CatalogUtils.getEntityUUId(entity), scorecardComputationTimestamp);
          }
        }
      } catch (Exception e) {
        log.warn("Error computing score", e);
      }
    }
  }

  @Override
  public boolean isFilterMatchingWithAnEntity(ScorecardFilter filter, BackstageCatalogEntity entity) {
    String entityType = BackstageCatalogEntityTypes.getEntityType(entity);
    String entityOwner = entity.getRelations()
                             .stream()
                             .filter(relation -> relation.getType().equalsIgnoreCase("ownedBy"))
                             .findFirst()
                             .map(relation -> relation.getTargetRef())
                             .orElse(BackstageCatalogEntityTypes.getEntityOwner(entity));
    String entityLifecycle = BackstageCatalogEntityTypes.getEntityLifecycle(entity);
    if (!filter.getKind().equalsIgnoreCase(entity.getKind())
        || (!filter.getType().equalsIgnoreCase("all") && entityType != null
            && !filter.getType().equalsIgnoreCase(entityType))
        || (!filter.getOwners().isEmpty() && entityOwner != null
            && !filter.getOwners()
                    .stream()
                    .map(filterOwner -> filterOwner.toLowerCase())
                    .toList()
                    .contains(entityOwner.toLowerCase()))
        || (!filter.getLifecycle().isEmpty() && entityLifecycle != null
            && !filter.getLifecycle().contains(entityLifecycle))) {
      return false;
    }
    List<BackstageCatalogEntity> entities = new ArrayList<>(Collections.singletonList(entity));
    filterEntitiesByTags(entities, filter.getTags());
    return !entities.isEmpty();
  }

  @Override
  public org.apache.commons.lang3.tuple.Pair<Boolean, Map<String, Set<ScopeInfo>>> isFilterMatchingWithCatalogEntity(
      ScorecardFilter filter, CatalogEntity entity, Map<String, Set<ScopeInfo>> scopeInfosForScopes) {
    String type = entity.getType();
    String owner = entity.getOwner();
    String lifecycle = entity.getSpec() != null ? (String) entity.getSpec().get("lifecycle") : null;
    String kind = filter.getKind().toLowerCase();

    List<String> scopesList =
        isEmpty(filter.getScopes()) ? Arrays.asList(catalogServiceHelper.getAllScopes()) : filter.getScopes();

    String scopes = String.join(",", scopesList);
    if (kind.equals("template")) {
      kind = "workflow";
    }

    Set<String> scopeParts =
        Arrays.stream(scopes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());

    Set<String> toBeResolvedScopes = scopeParts.stream()
                                         .filter(scope
                                             -> scopeInfosForScopes.keySet()
                                                    .stream()
                                                    .map(key -> key.split(","))
                                                    .flatMap(Arrays::stream)
                                                    .map(String::trim)
                                                    .noneMatch(scPart -> scPart.equals(scope)))
                                         .collect(Collectors.toSet());

    scopes = String.join(",", toBeResolvedScopes);

    List<ScopeInfo> scopeInfos;
    if (!isEmpty(scopes)) {
      scopeInfos =
          catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(entity.getAccountIdentifier(), scopes, null)
              .getLeft();
      scopeInfosForScopes.put(scopes, new HashSet<>(scopeInfos));
    } else {
      scopeInfos = scopeInfosForScopes.values().stream().flatMap(Set::stream).collect(Collectors.toList());
    }

    boolean isMatchingScopePresent = scopeInfos.stream().anyMatch(scopeInfo
        -> Objects.equals(scopeInfo.getAccountIdentifier(), entity.getAccountIdentifier())
            && Objects.equals(scopeInfo.getOrgIdentifier(), entity.getOrgIdentifier())
            && Objects.equals(scopeInfo.getProjectIdentifier(), entity.getProjectIdentifier())
            && Objects.equals(scopeInfo.getUniqueId(), entity.getParentUniqueId())
            && scopeInfo.getScopeType() == ScopeLevel.valueOf(entity.getScope()));

    if (!kind.equalsIgnoreCase(entity.getKind())
        || (!filter.getType().equalsIgnoreCase("all") && type != null && !filter.getType().equalsIgnoreCase(type))
        || (!filter.getOwners().isEmpty() && owner != null
            && filter.getOwners().stream().noneMatch(filterOwner -> filterOwner.equalsIgnoreCase(owner)))
        || (!filter.getLifecycle().isEmpty() && lifecycle != null && !filter.getLifecycle().contains(lifecycle))
        || (!isMatchingScopePresent)) {
      return org.apache.commons.lang3.tuple.Pair.of(Boolean.FALSE, scopeInfosForScopes);
    }
    List<CatalogEntity> entities = new ArrayList<>(Collections.singletonList(entity));
    filterCatalogEntitiesByTags(entities, filter.getTags());
    return org.apache.commons.lang3.tuple.Pair.of(!entities.isEmpty(), scopeInfosForScopes);
  }

  private Triple<CheckStatus.StatusEnum, String, List<EvaluationData>> getCheckStatusFailureReasonAndEvaluationData(
      IdpExpressionEvaluator evaluator, CheckEntity checkEntity) {
    String fullExpression;
    boolean isAdvancedCheck = checkEntity.getRuleStrategy().equals(CheckDetails.RuleStrategyEnum.ADVANCED);
    if (isAdvancedCheck) {
      fullExpression = CheckDetailsMapper.cleanComplexCheck(checkEntity.getExpression());
    } else {
      fullExpression = constructExpressionFromRules(
          checkEntity.getRules(), checkEntity.getRuleStrategy(), DATA_POINT_VALUE_KEY, false);
    }
    String expression = null;
    List<EvaluationData> allRulesEvaluationData = new ArrayList<>();
    for (Rule rule : checkEntity.getRules()) {
      EvaluationData evaluationData = new EvaluationData();
      Object value = null;
      Object errorMessage = null;
      try {
        evaluationData.setRuleExpression(getDisplayExpression(rule, CheckDetails.RuleStrategyEnum.ADVANCED));
        evaluationData.expectedValue((rule.getOperator()) + SPACE_SEPARATOR + (rule.getValue()));
        evaluationData.setRuleDescription(rule.getRuleDescription());

        expression = constructExpressionFromRules(
            Collections.singletonList(rule), checkEntity.getRuleStrategy(), DATA_POINT_VALUE_KEY, true);
        value = evaluator.evaluateExpression(expression, RETURN_NULL_IF_UNRESOLVED);
        String errorMessageExpression = constructExpressionFromRules(
            Collections.singletonList(rule), checkEntity.getRuleStrategy(), ERROR_MESSAGE_KEY, true);
        errorMessage = evaluator.evaluateExpression(errorMessageExpression, RETURN_NULL_IF_UNRESOLVED);
      } catch (Exception e) {
        log.warn("Expression evaluation failed while evaluating rule {} check {}", rule.getIdentifier(),
            checkEntity.getIdentifier(), e);
      }
      evaluationData.setActualValue(value != null ? value.toString() : "null");
      assert expression != null;
      if (CheckDetails.DefaultBehaviourEnum.PASS.equals(checkEntity.getDefaultBehaviour()) && value == null
          && (isEmpty((String) errorMessage) || ((String) errorMessage).contains(MISSING_DATA))) {
        int startIndex = fullExpression.indexOf(expression);
        int lastIndex = fullExpression.indexOf(SPACE_SEPARATOR, startIndex);
        if (lastIndex == -1) {
          lastIndex = fullExpression.length();
        }

        if (isAdvancedCheck) {
          fullExpression =
              fullExpression.replace(getDisplayExpression(rule, CheckDetails.RuleStrategyEnum.ADVANCED), "true");
        } else {
          fullExpression = fullExpression.replace(fullExpression.substring(startIndex, lastIndex), "true");
        }

      } else {
        if (isAdvancedCheck) {
          fullExpression = fullExpression.replace(
              getDisplayExpression(rule, CheckDetails.RuleStrategyEnum.ADVANCED), parseValue(value));
        } else {
          fullExpression = fullExpression.replace(expression, parseValue(value));
        }
      }
      allRulesEvaluationData.add(evaluationData);
    }
    Object value = null;
    try {
      value = evaluator.evaluateExpression(fullExpression, RETURN_NULL_IF_UNRESOLVED);
    } catch (Exception e) {
      log.error("Expression evaluation failed while evaluating check {}", checkEntity.getIdentifier(), e);
    }
    if (value == null) {
      log.warn("Could not evaluate check status for {}", checkEntity.getIdentifier());
      if (CheckDetails.DefaultBehaviourEnum.FAIL.equals(checkEntity.getDefaultBehaviour())) {
        return Triple.of(
            CheckStatus.StatusEnum.FAIL, getCheckFailureReason(evaluator, checkEntity), allRulesEvaluationData);
      }
      return Triple.of(CheckStatus.StatusEnum.valueOf(checkEntity.getDefaultBehaviour().toString()),
          getCheckFailureReason(evaluator, checkEntity), allRulesEvaluationData);
    } else {
      if (!(value instanceof Boolean)) {
        log.warn("Expected boolean assertion, got {} value for check {}", value, checkEntity.getIdentifier());
        return Triple.of(CheckStatus.StatusEnum.valueOf(checkEntity.getDefaultBehaviour().toString()),
            getCheckFailureReason(evaluator, checkEntity), allRulesEvaluationData);
      }
      if (!(boolean) value) {
        return Triple.of(
            CheckStatus.StatusEnum.FAIL, getCheckFailureReason(evaluator, checkEntity), allRulesEvaluationData);
      }
      return Triple.of(
          CheckStatus.StatusEnum.PASS, getCheckFailureReason(evaluator, checkEntity), allRulesEvaluationData);
    }
  }

  private String parseValue(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof String strValue) {
      try {
        return String.valueOf(Integer.parseInt(strValue));
      } catch (NumberFormatException e1) {
        try {
          return String.valueOf(Double.parseDouble(strValue));
        } catch (NumberFormatException e2) {
          return "\"" + strValue + "\"";
        }
      }
    }
    return String.valueOf(value);
  }

  private String getCheckFailureReason(IdpExpressionEvaluator evaluator, CheckEntity checkEntity) {
    StringBuilder reasonBuilder = new StringBuilder();
    for (Rule rule : checkEntity.getRules()) {
      try {
        String errorMessageExpression = constructExpressionFromRules(
            Collections.singletonList(rule), checkEntity.getRuleStrategy(), ERROR_MESSAGE_KEY, true);
        Object errorMessage = evaluator.evaluateExpression(errorMessageExpression, RETURN_NULL_IF_UNRESOLVED);
        String lhsExpression = constructExpressionFromRules(
            Collections.singletonList(rule), checkEntity.getRuleStrategy(), DATA_POINT_VALUE_KEY, true);
        Object lhsValue = evaluator.evaluateExpression(lhsExpression, RETURN_NULL_IF_UNRESOLVED);

        if (lhsValue == null || (lhsValue instanceof String && "null".equals(lhsValue))) {
          if ((errorMessage instanceof String) && !((String) errorMessage).isEmpty()) {
            reasonBuilder.append(String.format("Message: %s", errorMessage));
          } else if (!isEmpty(checkEntity.getFailMessage())) {
            reasonBuilder.append(String.format(". Message: %s", checkEntity.getFailMessage()));
          } else {
            reasonBuilder.append(String.format(
                "No data available for check '%s'. Please verify data source configuration.", checkEntity.getName()));
          }
        } else if ((errorMessage instanceof String) && !((String) errorMessage).isEmpty()) {
          if (StringUtils.isNotBlank(rule.getValue())) {
            reasonBuilder.append(String.format(
                "Expected %s %s. Actual %s. Message: %s", rule.getOperator(), rule.getValue(), lhsValue, errorMessage));
          } else {
            reasonBuilder.append(
                String.format("Expected %s. Actual %s. Message: %s", rule.getOperator(), lhsValue, errorMessage));
          }
        } else {
          if (StringUtils.isNotBlank(rule.getValue())) {
            reasonBuilder.append(
                String.format("Expected %s %s. Actual %s", rule.getOperator(), rule.getValue(), lhsValue));
          } else {
            reasonBuilder.append(String.format("Expected %s. Actual %s", rule.getOperator(), lhsValue));
          }
          if (!isEmpty(checkEntity.getFailMessage())) {
            reasonBuilder.append(String.format(". Message: %s", checkEntity.getFailMessage()));
          }
        }
      } catch (Exception e) {
        log.warn("Reason expression evaluation failed for check {}", checkEntity.getIdentifier(), e);
      }
      reasonBuilder.append(";").append(SPACE_SEPARATOR);
    }
    return reasonBuilder.toString().trim();
  }

  private void filterEntitiesByTags(List<BackstageCatalogEntity> entities, List<String> scorecardTags) {
    if (scorecardTags.isEmpty()) {
      return;
    }
    entities.removeIf(entity -> {
      List<String> tags =
          BackstageCatalogEntity.getValue(entity.getMetadata(), MetadataFieldConstants.TAGS, List.class);
      if (tags == null || tags.isEmpty()) {
        return true;
      }
      return !new HashSet<>(tags).containsAll(scorecardTags);
    });
  }

  private void filterCatalogEntitiesByTags(List<CatalogEntity> entities, List<String> scorecardTags) {
    if (scorecardTags.isEmpty()) {
      return;
    }
    entities.removeIf(entity -> {
      List<String> tags = entity.getTags();
      if (tags == null || tags.isEmpty()) {
        return true;
      }
      return !new HashSet<>(tags).containsAll(scorecardTags);
    });
  }

  private Map<String, List<DataFetchDTO>> getProviderDataToFetch(List<ScorecardAndChecks> scorecardsAndChecks) {
    Map<String, List<DataFetchDTO>> providerDataToFetch = new HashMap<>();

    for (ScorecardAndChecks scorecardAndChecks : scorecardsAndChecks) {
      List<CheckEntity> checks = scorecardAndChecks.getChecks();
      for (CheckEntity check : checks) {
        if (check.isCustom() && !check.isHarnessManaged()) {
          // TODO: custom expressions to be handled in a different way.
          // Maybe just return the list of dataSourceIdentifiers. Don't optimize (calling only certain DSLs) for these
          log.warn("Custom expressions are not supported yet; Check {}", check.getIdentifier());
          continue;
        }
        for (Rule rule : check.getRules()) {
          String dataSourceIdentifier = rule.getDataSourceIdentifier();
          List<DataFetchDTO> dataFetchDTOS = providerDataToFetch.getOrDefault(dataSourceIdentifier, new ArrayList<>());
          dataFetchDTOS.add(DataFetchDTO.builder()
                                .ruleIdentifier(rule.getIdentifier())
                                .dataPoint(DataPointEntity.builder().identifier(rule.getDataPointIdentifier()).build())
                                .inputValues(rule.getInputValues())
                                .build());
          providerDataToFetch.put(dataSourceIdentifier, dataFetchDTOS);
        }
      }
    }
    return providerDataToFetch;
  }
}
