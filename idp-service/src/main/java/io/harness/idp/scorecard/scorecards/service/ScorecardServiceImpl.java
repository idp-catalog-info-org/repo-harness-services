/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.backstage.utils.BackstageUtils.getEntityUniqueId;
import static io.harness.idp.catalog.utils.Constants.LIFECYCLE;
import static io.harness.idp.catalog.utils.Constants.OWNER;
import static io.harness.idp.catalog.utils.Constants.TAGS;
import static io.harness.idp.catalog.utils.Constants.TYPE;
import static io.harness.idp.common.Constants.BACKSTAGE_KINDS;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.idp.common.DateUtils.startOfTheDayInMilliseconds;
import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.DEFAULT_TIER_GROUP_IDENTIFIER;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.lang.String.format;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.clients.BackstageResourceClient;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.KindServiceHelper;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.checks.service.CheckService;
import io.harness.idp.scorecard.scorecards.beans.BackstageCatalogEntityFacets;
import io.harness.idp.scorecard.scorecards.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecards.beans.StatsMetadata;
import io.harness.idp.scorecard.scorecards.entity.EntityDiffUtils;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.entity.ScorecardStatsEntity;
import io.harness.idp.scorecard.scorecards.events.ScorecardCreateEvent;
import io.harness.idp.scorecard.scorecards.events.ScorecardDeleteEvent;
import io.harness.idp.scorecard.scorecards.events.ScorecardUpdateEvent;
import io.harness.idp.scorecard.scorecards.mappers.ScorecardAndChecksMapper;
import io.harness.idp.scorecard.scorecards.mappers.ScorecardDetailsMapper;
import io.harness.idp.scorecard.scorecards.mappers.ScorecardMapper;
import io.harness.idp.scorecard.scorecards.mappers.ScorecardStatsMapper;
import io.harness.idp.scorecard.scorecards.repositories.CountAndPercentage;
import io.harness.idp.scorecard.scorecards.repositories.ScorecardIdentifierAndStats;
import io.harness.idp.scorecard.scorecards.repositories.ScorecardRepository;
import io.harness.idp.scorecard.scorecards.repositories.ScorecardStatsDuplicateEntry;
import io.harness.idp.scorecard.scorecards.repositories.ScorecardStatsRepository;
import io.harness.idp.scorecard.tiergroups.service.TierGroupService;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.idp.v1.model.EntityFiltersResponse;
import io.harness.spec.server.idp.v1.model.Scorecard;
import io.harness.spec.server.idp.v1.model.ScorecardChecks;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsRequest;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsResponse;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.idp.v1.model.ScorecardStatsResponse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.transaction.support.TransactionTemplate;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class ScorecardServiceImpl implements ScorecardService {
  private final ScorecardRepository scorecardRepository;
  private final ScorecardStatsRepository scorecardStatsRepository;
  private final CheckService checkService;
  private final BackstageResourceClient backstageResourceClient;
  private final CatalogServiceHelper catalogServiceHelper;
  private final KindServiceHelper kindServiceHelper;
  private final TierGroupService tierGroupService;
  private final IdpCommonService idpCommonService;

  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private final OutboxService outboxService;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final String TYPE_FILTER = "spec.type";
  private static final String OWNERS_FILTER = "relations.ownedBy";
  private static final String TAGS_FILTER = "metadata.tags";
  private static final String LIFECYCLE_FILTER = "spec.lifecycle";
  private static final String CATALOG_API = "%s/idp/api/catalog/entity-facets?filter=kind=%s&facet=" + TYPE_FILTER
      + "&facet=" + OWNERS_FILTER + "&facet=" + TAGS_FILTER + "&facet=" + LIFECYCLE_FILTER;

  @Inject
  public ScorecardServiceImpl(ScorecardRepository scorecardRepository,
      ScorecardStatsRepository scorecardStatsRepository, CheckService checkService,
      BackstageResourceClient backstageResourceClient, TransactionTemplate transactionTemplate,
      OutboxService outboxService, CatalogServiceHelper catalogServiceHelper, KindServiceHelper kindServiceHelper,
      TierGroupService tierGroupService, IdpCommonService idpCommonService) {
    this.scorecardRepository = scorecardRepository;
    this.scorecardStatsRepository = scorecardStatsRepository;
    this.checkService = checkService;
    this.backstageResourceClient = backstageResourceClient;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
    this.catalogServiceHelper = catalogServiceHelper;
    this.kindServiceHelper = kindServiceHelper;
    this.tierGroupService = tierGroupService;
    this.idpCommonService = idpCommonService;
  }

  @Override
  public List<Scorecard> getAllScorecardsAndChecksDetails(String accountIdentifier) {
    List<Scorecard> scorecards = new ArrayList<>();
    List<ScorecardEntity> scorecardEntities = scorecardRepository.findByAccountIdentifier(accountIdentifier);
    Set<String> uniqueCheckIds = new HashSet<>();
    for (ScorecardEntity scorecardEntity : scorecardEntities) {
      Set<String> checkIds =
          scorecardEntity.getChecks().stream().map(ScorecardEntity.Check::getIdentifier).collect(Collectors.toSet());
      uniqueCheckIds.addAll(checkIds);
    }

    boolean tierAnalyticsEnabled = idpCommonService.idpScorecardTiersEnabled(accountIdentifier);
    Map<String, CountAndPercentage> countAndPercentageMap = new HashMap<>();
    if (!tierAnalyticsEnabled) {
      List<String> scorecardIdentifiers =
          scorecardEntities.stream().map(ScorecardEntity::getIdentifier).collect(Collectors.toList());
      List<ScorecardIdentifierAndStats> scorecardIdentifierAndStats =
          scorecardStatsRepository.findLastUpdatedByScorecardIdentifiers(accountIdentifier, scorecardIdentifiers);
      for (ScorecardIdentifierAndStats scorecardIdentifierAndStat : scorecardIdentifierAndStats) {
        String scorecardIdentifier = scorecardIdentifierAndStat.getScorecardIdentifier();
        long lastUpdatedAt = scorecardIdentifierAndStat.getScorecardStatsEntity().getLastUpdatedAt();
        countAndPercentageMap.put(scorecardIdentifier,
            scorecardStatsRepository.computeScoresPercentageByScorecard(
                accountIdentifier, scorecardIdentifier, startOfTheDayInMilliseconds(lastUpdatedAt)));
      }
    }

    for (ScorecardEntity scorecardEntity : scorecardEntities) {
      scorecards.add(
          ScorecardMapper.toDTO(scorecardEntity, getIdentifierCheckEntityMapping(accountIdentifier, uniqueCheckIds),
              countAndPercentageMap.get(scorecardEntity.getIdentifier()), tierAnalyticsEnabled, accountIdentifier));
    }
    return scorecards;
  }

  @Override
  public List<ScorecardAndChecks> getAllScorecardAndChecks(
      String accountIdentifier, List<String> scorecardIdentifiers) {
    List<ScorecardEntity> scorecardEntities;
    if (scorecardIdentifiers == null || scorecardIdentifiers.isEmpty()) {
      scorecardEntities = scorecardRepository.findByAccountIdentifierAndPublished(accountIdentifier, true);
    } else {
      scorecardEntities =
          scorecardRepository.findByAccountIdentifierAndIdentifierIn(accountIdentifier, scorecardIdentifiers);
    }
    List<String> checkIdentifiers = scorecardEntities.stream()
                                        .flatMap(scorecardEntity -> scorecardEntity.getChecks().stream())
                                        .map(ScorecardEntity.Check::getIdentifier)
                                        .collect(Collectors.toList());
    Map<String, CheckEntity> checkEntityMap =
        checkService.getActiveChecks(accountIdentifier, checkIdentifiers)
            .stream()
            .collect(Collectors.toMap(CheckEntity::getIdentifier, Function.identity()));
    List<ScorecardAndChecks> scorecardDetailsList = new ArrayList<>();
    for (ScorecardEntity scorecardEntity : scorecardEntities) {
      List<CheckEntity> checksList = scorecardEntity.getChecks()
                                         .stream()
                                         .filter(check -> checkEntityMap.containsKey(check.getIdentifier()))
                                         .map(check -> checkEntityMap.get(check.getIdentifier()))
                                         .collect(Collectors.toList());
      scorecardDetailsList.add(ScorecardAndChecksMapper.toDTO(scorecardEntity, checksList));
    }
    return scorecardDetailsList;
  }

  @Override
  public void saveScorecard(ScorecardDetailsRequest scorecardDetailsRequest, String accountIdentifier) {
    ScorecardEntity existingScorecard = scorecardRepository.findByAccountIdentifierAndIdentifier(
        accountIdentifier, scorecardDetailsRequest.getScorecard().getIdentifier());

    if (existingScorecard != null) {
      throw new InvalidRequestException(
          String.format("A scorecard with identifier '%s' already exists. Please use a different identifier.",
              scorecardDetailsRequest.getScorecard().getIdentifier()));
    }

    validateScorecardSaveRequest(accountIdentifier, scorecardDetailsRequest);
    validateChecks(scorecardDetailsRequest.getChecks(), accountIdentifier);
    boolean scorecardTiersEnabled = idpCommonService.idpScorecardTiersEnabled(accountIdentifier);
    if (scorecardTiersEnabled
        || !isTierGroupIdentifierBlank(scorecardDetailsRequest.getScorecard().getTierGroupIdentifier())) {
      scorecardDetailsRequest.getScorecard().setTierGroupIdentifier(
          validateTierGroup(accountIdentifier, scorecardDetailsRequest.getScorecard().getTierGroupIdentifier()));
    }
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      ScorecardEntity scorecardEntityToSave =
          setDefaultScopesForScorecard(ScorecardDetailsMapper.fromDTO(scorecardDetailsRequest, accountIdentifier));

      ScorecardEntity savedScorecardEntity = scorecardRepository.save(scorecardEntityToSave);

      ScorecardDetailsResponse savedScorecardDetailsResponse = ScorecardDetailsMapper.toDTO(savedScorecardEntity,
          getIdentifierCheckEntityMapping(accountIdentifier,
              savedScorecardEntity.getChecks()
                  .stream()
                  .map(ScorecardEntity.Check::getIdentifier)
                  .collect(Collectors.toSet())),
          null, scorecardTiersEnabled, accountIdentifier);

      outboxService.save(new ScorecardCreateEvent(accountIdentifier, savedScorecardDetailsResponse));
      return true;
    }));
  }

  @Override
  public void updateScorecard(ScorecardDetailsRequest scorecardDetailsRequest, String accountIdentifier) {
    validateScorecardSaveRequest(accountIdentifier, scorecardDetailsRequest);
    validateChecks(scorecardDetailsRequest.getChecks(), accountIdentifier);
    boolean scorecardTiersEnabled = idpCommonService.idpScorecardTiersEnabled(accountIdentifier);
    boolean tierGroupIdentifierOmitted =
        isTierGroupIdentifierBlank(scorecardDetailsRequest.getScorecard().getTierGroupIdentifier());
    if (scorecardTiersEnabled || !tierGroupIdentifierOmitted) {
      scorecardDetailsRequest.getScorecard().setTierGroupIdentifier(
          validateTierGroup(accountIdentifier, scorecardDetailsRequest.getScorecard().getTierGroupIdentifier()));
    } else {
      ScorecardEntity existingScorecard = scorecardRepository.findByAccountIdentifierAndIdentifier(
          accountIdentifier, scorecardDetailsRequest.getScorecard().getIdentifier());
      if (existingScorecard != null) {
        String existingTierGroupIdentifier = existingScorecard.getTierGroupIdentifier();
        scorecardDetailsRequest.getScorecard().setTierGroupIdentifier(
            isTierGroupIdentifierBlank(existingTierGroupIdentifier) ? null : existingTierGroupIdentifier.trim());
      }
    }

    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      ScorecardEntity oldScorecardEntity = scorecardRepository.findByAccountIdentifierAndIdentifier(
          accountIdentifier, scorecardDetailsRequest.getScorecard().getIdentifier());

      ScorecardDetailsResponse oldScorecardDetails = ScorecardDetailsMapper.toDTO(oldScorecardEntity,
          getIdentifierCheckEntityMapping(accountIdentifier,
              oldScorecardEntity.getChecks()
                  .stream()
                  .map(ScorecardEntity.Check::getIdentifier)
                  .collect(Collectors.toSet())),
          null, scorecardTiersEnabled, accountIdentifier);

      ScorecardEntity updatedScorecardEntity =
          scorecardRepository.update(ScorecardDetailsMapper.fromDTO(scorecardDetailsRequest, accountIdentifier));

      ScorecardDetailsResponse updatedScorecardDetails = ScorecardDetailsMapper.toDTO(updatedScorecardEntity,
          getIdentifierCheckEntityMapping(accountIdentifier,
              updatedScorecardEntity.getChecks()
                  .stream()
                  .map(ScorecardEntity.Check::getIdentifier)
                  .collect(Collectors.toSet())),
          null, scorecardTiersEnabled, accountIdentifier);

      if (EntityDiffUtils.isScorecardUpdated(oldScorecardEntity, updatedScorecardEntity)) {
        outboxService.save(new ScorecardUpdateEvent(accountIdentifier, updatedScorecardDetails, oldScorecardDetails));
      } else {
        log.info("No update to scorecard, skipping audit publish for the account: {}, scorecard: {}", accountIdentifier,
            oldScorecardEntity.getIdentifier());
      }

      return true;
    }));
  }

  @Override
  public ScorecardDetailsResponse getScorecardDetails(String accountIdentifier, String identifier) {
    ScorecardEntity scorecardEntity =
        scorecardRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    if (scorecardEntity == null) {
      throw new InvalidRequestException(String.format("Scorecard details not found for scorecardId [%s]", identifier));
    }
    Set<String> checkIds =
        scorecardEntity.getChecks().stream().map(ScorecardEntity.Check::getIdentifier).collect(Collectors.toSet());

    boolean tierAnalyticsEnabled = idpCommonService.idpScorecardTiersEnabled(accountIdentifier);
    CountAndPercentage countAndPercentage = null;
    if (!tierAnalyticsEnabled) {
      ScorecardIdentifierAndStats scorecardIdentifierAndStats =
          scorecardStatsRepository.findLastUpdatedByScorecardIdentifiers(accountIdentifier, List.of(identifier))
              .stream()
              .filter(
                  scorecardIdentifierAndStat -> scorecardIdentifierAndStat.getScorecardIdentifier().equals(identifier))
              .findFirst()
              .orElse(null);
      if (scorecardIdentifierAndStats != null) {
        long updatedAt = scorecardIdentifierAndStats.getScorecardStatsEntity().getLastUpdatedAt();
        countAndPercentage = scorecardStatsRepository.computeScoresPercentageByScorecard(
            accountIdentifier, identifier, startOfTheDayInMilliseconds(updatedAt));
      }
    }

    return ScorecardDetailsMapper.toDTO(scorecardEntity, getIdentifierCheckEntityMapping(accountIdentifier, checkIds),
        countAndPercentage, tierAnalyticsEnabled, accountIdentifier);
  }

  @Override
  public List<ScorecardFilter> getScorecardFilters(String accountIdentifier, List<String> scorecardIdentifiers) {
    List<ScorecardEntity> scorecardEntities =
        scorecardRepository.findByAccountIdentifierAndIdentifierIn(accountIdentifier, scorecardIdentifiers);
    return scorecardEntities.stream()
        .filter(ScorecardEntity::isPublished)
        .map(ScorecardEntity::getFilter)
        .collect(Collectors.toList());
  }

  private void validateScorecardSaveRequest(String accountIdentifier, ScorecardDetailsRequest scorecardDetailsRequest) {
    if (scorecardDetailsRequest.getScorecard().isPublished() && isEmpty(scorecardDetailsRequest.getChecks())) {
      throw new InvalidRequestException("Atleast one check should be present for publishing scorecard");
    }
    if (scorecardDetailsRequest.getScorecard().getIdentifier() == null
        || scorecardDetailsRequest.getScorecard().getIdentifier().trim().isEmpty()) {
      throw new InvalidRequestException("Scorecard Identifier can't be empty");
    }
    kindServiceHelper.validateKindIfExist(
        accountIdentifier, scorecardDetailsRequest.getScorecard().getFilter().getKind());
  }

  private void validateChecks(List<ScorecardChecks> scorecardChecks, String harnessAccount) {
    Set<String> checkIds = scorecardChecks.stream().map(ScorecardChecks::getIdentifier).collect(Collectors.toSet());
    Map<String, CheckEntity> checkEntityMap = getIdentifierCheckEntityMapping(harnessAccount, checkIds);
    List<String> missingChecks = new ArrayList<>();
    scorecardChecks.forEach(scorecardCheck -> {
      String accountId = scorecardCheck.isCustom() ? harnessAccount : GLOBAL_ACCOUNT_ID;
      CheckEntity checkEntity = checkEntityMap.get(accountId + DOT_SEPARATOR + scorecardCheck.getIdentifier());
      if (checkEntity == null) {
        throw new InvalidRequestException(
            format("Error while saving scorecard. Could not find check %s", scorecardCheck.getIdentifier()));
      }
      if (checkEntity.isDeleted()) {
        missingChecks.add(scorecardCheck.getIdentifier());
      }
    });

    if (isNotEmpty(missingChecks)) {
      throw new InvalidRequestException(
          format("Error while saving scorecard. Please remove deleted checks %s", checkIds));
    }
  }

  private String validateTierGroup(String accountIdentifier, String tierGroupIdentifier) {
    if (isTierGroupIdentifierBlank(tierGroupIdentifier)) {
      throw new InvalidRequestException("Tier group identifier is required for scorecard");
    }
    String normalizedTierGroupIdentifier = tierGroupIdentifier.trim();
    tierGroupService.validateTierGroupReference(accountIdentifier, normalizedTierGroupIdentifier);
    return normalizedTierGroupIdentifier;
  }

  private boolean isTierGroupIdentifierBlank(String tierGroupIdentifier) {
    return tierGroupIdentifier == null || tierGroupIdentifier.trim().isEmpty();
  }

  @Override
  public String ensureScorecardTierGroupIdentifier(String accountIdentifier, ScorecardEntity scorecard) {
    if (!isTierGroupIdentifierBlank(scorecard.getTierGroupIdentifier())) {
      String tierGroupIdentifier = scorecard.getTierGroupIdentifier().trim();
      if (tierGroupService.getActiveTierGroup(accountIdentifier, tierGroupIdentifier) != null) {
        return tierGroupIdentifier;
      }
    }
    return assignDefaultTierGroupToScorecard(accountIdentifier, scorecard);
  }

  private String assignDefaultTierGroupToScorecard(String accountIdentifier, ScorecardEntity scorecard) {
    String originalTierGroupIdentifier = scorecard.getTierGroupIdentifier();
    tierGroupService.createDefaultTierGroupIfAbsent(accountIdentifier);

    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      ScorecardEntity oldScorecard = scorecard.toBuilder().build();
      ScorecardDetailsResponse oldScorecardDetails = toScorecardDetailsForTierGroupAudit(oldScorecard);

      ScorecardEntity updatedScorecard =
          scorecardRepository.update(scorecard.toBuilder().tierGroupIdentifier(DEFAULT_TIER_GROUP_IDENTIFIER).build());
      ScorecardDetailsResponse updatedScorecardDetails = toScorecardDetailsForTierGroupAudit(updatedScorecard);
      outboxService.save(new ScorecardUpdateEvent(accountIdentifier, updatedScorecardDetails, oldScorecardDetails));
      return true;
    }));
    scorecard.setTierGroupIdentifier(DEFAULT_TIER_GROUP_IDENTIFIER);
    log.warn(
        "Referenced tier group is not active; assigned scorecard to the default tier group. accountIdentifier: {}, "
            + "scorecardIdentifier: {}, originalTierGroupIdentifier: {}, newTierGroupIdentifier: {}",
        accountIdentifier, scorecard.getIdentifier(), originalTierGroupIdentifier, DEFAULT_TIER_GROUP_IDENTIFIER);
    return DEFAULT_TIER_GROUP_IDENTIFIER;
  }

  private ScorecardDetailsResponse toScorecardDetailsForTierGroupAudit(ScorecardEntity scorecard) {
    return ScorecardDetailsMapper.toDTO(scorecard,
        getIdentifierCheckEntityMapping(scorecard.getAccountIdentifier(),
            scorecard.getChecks().stream().map(ScorecardEntity.Check::getIdentifier).collect(Collectors.toSet())),
        null, true, scorecard.getAccountIdentifier());
  }

  @Override
  public void deleteScorecard(String accountIdentifier, String identifier) {
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      ScorecardEntity toBeDeletedScorecardEntity =
          scorecardRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);

      DeleteResult deleteResult = scorecardRepository.delete(accountIdentifier, identifier);

      if (deleteResult.getDeletedCount() == 0 || toBeDeletedScorecardEntity == null) {
        throw new InvalidRequestException("Could not delete scorecard");
      }
      ScorecardDetailsResponse toBeDeletedScorecardDetails = ScorecardDetailsMapper.toDTO(toBeDeletedScorecardEntity,
          getIdentifierCheckEntityMapping(accountIdentifier,
              toBeDeletedScorecardEntity.getChecks()
                  .stream()
                  .map(ScorecardEntity.Check::getIdentifier)
                  .collect(Collectors.toSet())),
          null, idpCommonService.idpScorecardTiersEnabled(accountIdentifier), accountIdentifier);

      outboxService.save(new ScorecardDeleteEvent(accountIdentifier, toBeDeletedScorecardDetails));
      return true;
    }));
  }

  @Override
  public List<EntityFiltersResponse> getAllEntityFacets(String accountIdentifier, String kind) {
    List<EntityFiltersResponse> entityFiltersResponses = new ArrayList<>();
    BackstageCatalogEntityFacets backstageCatalogEntityFacets = getEntityResponse(accountIdentifier, kind);
    populateFacets(backstageCatalogEntityFacets, entityFiltersResponses);
    return entityFiltersResponses;
  }

  @Override
  public ScorecardStatsResponse getScorecardStats(String accountIdentifier, String identifier) {
    ScorecardEntity scorecardEntity =
        scorecardRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    if (scorecardEntity == null) {
      throw new InvalidRequestException(String.format("Scorecard not found for scorecardId [%s]", identifier));
    }
    ScorecardIdentifierAndStats scorecardIdentifierAndStats =
        scorecardStatsRepository.findLastUpdatedByScorecardIdentifiers(accountIdentifier, List.of(identifier)).get(0);
    if (scorecardIdentifierAndStats == null) {
      throw new InvalidRequestException(String.format("Scorecard stats not found for scorecardId [%s]", identifier));
    }
    long updatedAt = scorecardIdentifierAndStats.getScorecardStatsEntity().getLastUpdatedAt();
    List<ScorecardStatsEntity> scorecardStatsEntities =
        scorecardStatsRepository.findByAccountIdentifierAndScorecardIdentifierAndLastUpdatedAtGreaterThan(
            accountIdentifier, identifier, startOfTheDayInMilliseconds(updatedAt));
    return ScorecardStatsMapper.toDTO(scorecardStatsEntities, scorecardEntity.getName(),
        idpCommonService.idpScorecardTiersEnabled(accountIdentifier));
  }

  @Override
  public List<String> getScorecardIdentifiers(String accountIdentifier, String checkIdentifier, Boolean custom) {
    return scorecardRepository.findByCheckIdentifierAndIsCustom(accountIdentifier, checkIdentifier, custom)
        .stream()
        .map(ScorecardEntity::getIdentifier)
        .collect(Collectors.toList());
  }

  @Override
  public void migrateEntityIdentifier(Map<String, String> entityIdentifiersMap, String accountIdentifier) {
    List<String> entityIdentifiers = scorecardStatsRepository.findUniqueEntityIdentifiers(accountIdentifier);
    log.info("Totally {} unique records present in ScorecardStats collection for account {}", entityIdentifiers.size(),
        accountIdentifier);
    entityIdentifiers.forEach(entityIdentifier -> {
      if (entityIdentifiersMap.containsKey(entityIdentifier)) {
        UpdateResult updateResult = scorecardStatsRepository.updateEntityIdentifier(
            accountIdentifier, entityIdentifier, entityIdentifiersMap.get(entityIdentifier));
        log.info("Totally {} records modified in ScorecardStats collection for account {}, identifier {}",
            updateResult.getModifiedCount(), accountIdentifier, entityIdentifiersMap.get(entityIdentifier));
      }
    });
  }

  @Override
  public void modifyEntityIdentifier(String accountIdentifier) {
    List<String> entityIdentifiers = scorecardStatsRepository.findUniqueEntityIdentifiers(accountIdentifier);
    log.info("Totally {} unique records present in ScorecardStats collection for account {}", entityIdentifiers.size(),
        accountIdentifier);
    entityIdentifiers.forEach(entityIdentifier -> {
      String[] kindNamespaceAndName = entityIdentifier.split("/");
      if (kindNamespaceAndName.length == 3 && BACKSTAGE_KINDS.contains(kindNamespaceAndName[0])) {
        String modifiedEntityIdentifier =
            getEntityUniqueId(kindNamespaceAndName[1], kindNamespaceAndName[0], kindNamespaceAndName[2]);
        try {
          UpdateResult updateResult = scorecardStatsRepository.updateEntityIdentifier(
              accountIdentifier, entityIdentifier, modifiedEntityIdentifier);
          log.info("Totally {} records modified in ScorecardStats collection for account {}, identifier {}",
              updateResult.getModifiedCount(), accountIdentifier, entityIdentifier);
        } catch (Exception e) {
          log.error("Error occurred while modifying ScorecardStats collection for account {}, identifier {}",
              accountIdentifier, entityIdentifier, e);
        }
      }
    });
  }

  @Override
  public void modifyEntityIdentifierForIdpV2(String accountIdentifier, Set<String> conflictedEntityUids) {
    List<ScorecardStatsDuplicateEntry> duplicateEntries =
        scorecardStatsRepository.findDuplicateEntities(accountIdentifier);
    List<String> idsToBeRemoved = new ArrayList<>();
    for (ScorecardStatsDuplicateEntry entry : duplicateEntries) {
      String[] namespaceKindName = entry.getEntityIdentifier().split("/");
      if (namespaceKindName.length == 3 && namespaceKindName[0].equals("account")
          && namespaceKindName[0].contains(".")) {
        continue;
      }
      log.info("Duplicate entries found in ScorecardStats collection for accountId: {}, scorecardIdentifier: {}, "
              + "checkIdentifier: {}, ids: {}",
          entry.getAccountIdentifier(), entry.getEntityIdentifier(), entry.getScorecardIdentifier(),
          entry.getDuplicates());
      idsToBeRemoved.addAll(entry.getDuplicates().subList(1, entry.getDuplicates().size()).stream().toList());
    }
    if (!isEmpty(idsToBeRemoved)) {
      scorecardStatsRepository.deleteAllById(idsToBeRemoved);
    }
    List<ScorecardStatsEntity> scorecardStatsEntities =
        scorecardStatsRepository.findByAccountIdentifier(accountIdentifier);
    log.info("Totally {} records present in ScorecardStats collection for account {}", scorecardStatsEntities.size(),
        accountIdentifier);
    List<ScorecardStatsEntity> entitiesToSave = new ArrayList<>();
    for (ScorecardStatsEntity scorecardStatsEntity : scorecardStatsEntities) {
      String entityUid = scorecardStatsEntity.getEntityIdentifier();
      String[] namespaceKindName = entityUid.split("/");
      if (namespaceKindName.length == 3 && !namespaceKindName[0].equals("account")
          && !namespaceKindName[0].contains(".")) {
        String namespace = namespaceKindName[0].toLowerCase();
        String kind = namespaceKindName[1].toLowerCase();
        String name = namespaceKindName[2].toLowerCase();
        String modifiedEntityUid = "account/" + kind + "/" + name;
        String modifiedEntityUidForConflict = "account/" + kind + "/" + namespace + "_" + name;
        if (conflictedEntityUids.contains(modifiedEntityUidForConflict)) {
          name = namespace + "_" + name;
          modifiedEntityUid = modifiedEntityUidForConflict;
        }
        scorecardStatsEntity.setEntityIdentifier(modifiedEntityUid);
        StatsMetadata metadata = scorecardStatsEntity.getMetadata();
        if (metadata != null) {
          metadata.setNamespace("account");
          metadata.setName(name);
          scorecardStatsEntity.setMetadata(metadata);
        }
        boolean isScorecardStatsEntityPresent = scorecardStatsEntities.stream().anyMatch(entity
            -> entity.getEntityIdentifier().equals(scorecardStatsEntity.getEntityIdentifier())
                && entity.getScorecardIdentifier().equals(scorecardStatsEntity.getScorecardIdentifier()));
        if (!isScorecardStatsEntityPresent) {
          entitiesToSave.add(scorecardStatsEntity);
        }
      }
    }
    log.info("Totally {} records to be modified in ScorecardStats collection for account {}", entitiesToSave.size(),
        accountIdentifier);
    scorecardStatsRepository.saveAll(entitiesToSave);
  }

  @Override
  public void modifyScopeForEntityIdentifier(
      String accountIdentifier, String existingEntityIdentifier, String modifiedEntityIdentifier) {
    try {
      UpdateResult updateResult = scorecardStatsRepository.updateEntityIdentifier(
          accountIdentifier, existingEntityIdentifier, modifiedEntityIdentifier);
      log.info("Totally {} records modified in ScorecardStats collection for IDP 2.0 MigrationAPI Operation for "
              + "account {}, identifier {}",
          updateResult.getModifiedCount(), accountIdentifier, existingEntityIdentifier);
    } catch (Exception e) {
      log.error("Error occurred while modifying ScorecardStats collection for IDP 2.0 MigrationAPI Operation for "
              + "account {}, identifier {}",
          accountIdentifier, existingEntityIdentifier, e);
    }
  }

  @Override
  public void addScopeToScorecardsForAccount(String accountIdentifier) {
    List<ScorecardEntity> scorecardEntities = scorecardRepository.findByAccountIdentifier(accountIdentifier);
    List<String> scopes = new ArrayList<>();
    scopes.add("account.*");
    if (!isEmpty(scorecardEntities)) {
      for (ScorecardEntity scorecardEntity : scorecardEntities) {
        ScorecardFilter scorecardFilter = scorecardEntity.getFilter();
        scorecardFilter.setScopes(scopes);
        scorecardEntity.setFilter(scorecardFilter);
      }
    }
    scorecardRepository.saveAll(scorecardEntities);
  }

  private BackstageCatalogEntityFacets getEntityResponse(String accountIdentifier, String kind) {
    String url = String.format(CATALOG_API, accountIdentifier, kind);
    return mapper.convertValue(
        getGeneralResponse(backstageResourceClient.getCatalogEntityFacets(url)), BackstageCatalogEntityFacets.class);
  }

  private void populateFacets(
      BackstageCatalogEntityFacets backstageCatalogEntityFacets, List<EntityFiltersResponse> entityFiltersResponses) {
    for (Map.Entry<String, List<BackstageCatalogEntityFacets.FacetType>> entry :
        backstageCatalogEntityFacets.getFacets().entrySet()) {
      EntityFiltersResponse entityFiltersResponse;
      switch (entry.getKey()) {
        case TYPE_FILTER:
          entityFiltersResponse = new EntityFiltersResponse();
          entityFiltersResponse.setFilter(TYPE);
          entityFiltersResponse.setValues(entry.getValue()
                                              .stream()
                                              .map(BackstageCatalogEntityFacets.FacetType::getValue)
                                              .collect(Collectors.toList()));
          entityFiltersResponses.add(entityFiltersResponse);
          break;
        case OWNERS_FILTER:
          entityFiltersResponse = new EntityFiltersResponse();
          entityFiltersResponse.setFilter(OWNER);
          entityFiltersResponse.setValues(entry.getValue()
                                              .stream()
                                              .map(BackstageCatalogEntityFacets.FacetType::getValue)
                                              .collect(Collectors.toList()));
          entityFiltersResponses.add(entityFiltersResponse);
          break;
        case TAGS_FILTER:
          entityFiltersResponse = new EntityFiltersResponse();
          entityFiltersResponse.setFilter(TAGS);
          entityFiltersResponse.setValues(entry.getValue()
                                              .stream()
                                              .map(BackstageCatalogEntityFacets.FacetType::getValue)
                                              .collect(Collectors.toList()));
          entityFiltersResponses.add(entityFiltersResponse);
          break;
        case LIFECYCLE_FILTER:
          entityFiltersResponse = new EntityFiltersResponse();
          entityFiltersResponse.setFilter(LIFECYCLE);
          entityFiltersResponse.setValues(entry.getValue()
                                              .stream()
                                              .map(BackstageCatalogEntityFacets.FacetType::getValue)
                                              .collect(Collectors.toList()));
          entityFiltersResponses.add(entityFiltersResponse);
          break;
      }
    }
  }

  public Map<String, CheckEntity> getIdentifierCheckEntityMapping(String accountIdentifier, Set<String> checkIds) {
    return checkService.getChecksByAccountIdAndIdentifiers(accountIdentifier, checkIds)
        .stream()
        .collect(Collectors.toMap(checkEntity
            -> checkEntity.getAccountIdentifier() + DOT_SEPARATOR + checkEntity.getIdentifier(),
            checkEntity -> checkEntity));
  }

  private ScorecardEntity setDefaultScopesForScorecard(ScorecardEntity scorecardEntity) {
    ScorecardFilter scorecardFilter = scorecardEntity.getFilter();

    if (scorecardFilter != null && isEmpty(scorecardFilter.getScopes())) {
      scorecardFilter.setScopes(Arrays.asList(catalogServiceHelper.getAllScopes()));
    }
    scorecardEntity.setFilter(scorecardFilter);
    return scorecardEntity;
  }
}
