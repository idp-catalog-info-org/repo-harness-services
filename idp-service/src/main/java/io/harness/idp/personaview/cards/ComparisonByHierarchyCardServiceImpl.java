/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.personaview.cards;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.catalog.utils.Constants.HIERARCHY_KIND;
import static io.harness.idp.personaview.PersonaViewConstants.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.idp.aggregation.rules.repositories.AggregationRuleRepository;
import io.harness.idp.catalog.beans.HierarchyEntityCount;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.homepage.service.CardService;
import io.harness.idp.personaview.entities.PersonaViewEntity;
import io.harness.idp.personaview.repositories.PersonaViewRepository;
import io.harness.idp.scorecard.scores.repositories.ScoreEntityByScorecardIdentifierEntityIdentifier;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;
import io.harness.spec.server.idp.v1.model.Card;
import io.harness.spec.server.idp.v1.model.ComparisonByHierarchyCardDataRequest;
import io.harness.spec.server.idp.v1.model.ComparisonByHierarchyCardDataResponse;
import io.harness.spec.server.idp.v1.model.ComparisonByHierarchyColumn;
import io.harness.spec.server.idp.v1.model.ComparisonByHierarchyNode;
import io.harness.spec.server.idp.v1.model.ComparisonByHierarchyRow;
import io.harness.spec.server.idp.v1.model.ComparisonByHierarchyScope;
import io.harness.spec.server.idp.v1.model.ScorecardComplianceCounts;

import com.google.inject.Inject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class ComparisonByHierarchyCardServiceImpl implements ComparisonByHierarchyCardService {
  private static final String LOG_PREFIX = "[ComparisonByHierarchyCard]";

  /** Tier thresholds for scorecard compliance. Hardcoded for v1; will move to scorecard config later. */
  static final int GOLD_THRESHOLD = 75;
  static final int SILVER_THRESHOLD = 50;

  /** Maximum number of aggregation-rule columns to render in the card. */
  static final int DEFAULT_RULE_LIMIT = 5;

  /** Maximum number of rows returned in the card payload. */
  static final int MAX_ROWS = 5;

  /**
   * Only scores computed within this window feed the compliance counts. Scorecards recompute (at most)
   * daily and every run rewrites a fresh document for each entity, so a 1-day window plus a 3-hour grace
   * (to absorb a delayed/long compute cycle) always covers the latest score for every actively-scored
   * entity while keeping the score aggregation's in-memory sort small.
   */
  static final long SCORE_FRESHNESS_WINDOW_MILLIS = (24 + 3) * 60L * 60L * 1000L;

  /**
   * Reserved, always-present leading column carrying the number of catalog entities scoped to each node.
   * This is a computed count (not an aggregation rule); its value lives under the same reserved key inside
   * {@code row.values}. Rows are ordered by this count, descending.
   */
  static final String ENTITY_COUNT_COLUMN_ID = "entity_count";
  static final String ENTITY_COUNT_COLUMN_NAME = "Entities";

  private static final String PERSONA_VIEW_NOT_FOUND = "Persona view not found for identifier - %s";
  private static final String CARD_NOT_ON_VIEW = "Persona view '%s' does not include a Comparison by Hierarchy card";
  private static final String UNKNOWN_AGGREGATION_RULES = "Unknown aggregation_rule_ids: %s";

  private final PersonaViewRepository personaViewRepository;
  private final CardService cardService;
  private final AggregationRuleRepository aggregationRuleRepository;
  private final CatalogEntityRepository catalogEntityRepository;
  private final ScoreRepository scoreRepository;
  private final IdpCommonService idpCommonService;
  private final ScorecardComplianceCache complianceCache;

  @Inject
  public ComparisonByHierarchyCardServiceImpl(PersonaViewRepository personaViewRepository, CardService cardService,
      AggregationRuleRepository aggregationRuleRepository, CatalogEntityRepository catalogEntityRepository,
      ScoreRepository scoreRepository, IdpCommonService idpCommonService, ScorecardComplianceCache complianceCache) {
    this.personaViewRepository = personaViewRepository;
    this.cardService = cardService;
    this.aggregationRuleRepository = aggregationRuleRepository;
    this.catalogEntityRepository = catalogEntityRepository;
    this.scoreRepository = scoreRepository;
    this.idpCommonService = idpCommonService;
    this.complianceCache = complianceCache;
  }

  @Override
  public ComparisonByHierarchyCardDataResponse getData(
      String accountIdentifier, String personaViewIdentifier, ComparisonByHierarchyCardDataRequest request) {
    long startMs = System.currentTimeMillis();
    log.info("{} getData account={} personaView={} scope={}", LOG_PREFIX, accountIdentifier, personaViewIdentifier,
        request == null ? null : request.getScope());

    ComparisonByHierarchyScope scope = validateRequestAndExtractScope(request);
    validatePersonaViewIncludesCard(accountIdentifier, personaViewIdentifier);

    List<AggregationRuleEntity> rules = resolveAggregationRules(accountIdentifier, request.getAggregationRuleIds());
    log.info("{} resolved {} aggregation-rule columns: {}", LOG_PREFIX, rules.size(),
        rules.stream().map(AggregationRuleEntity::getIdentifier).collect(Collectors.toList()));

    // entity_count is a mandatory leading column; aggregation-rule columns follow in requested order.
    List<ComparisonByHierarchyColumn> columns = new ArrayList<>(rules.size() + 1);
    columns.add(new ComparisonByHierarchyColumn().id(ENTITY_COUNT_COLUMN_ID).name(ENTITY_COUNT_COLUMN_NAME));
    rules.forEach(rule -> columns.add(new ComparisonByHierarchyColumn().id(rule.getIdentifier()).name(rule.getName())));

    // The aggregation ranks every node by entity count and returns only the top MAX_ROWS (sort + limit in
    // Mongo), so we then fetch just those node docs and pay the per-node scorecard query only for them.
    List<HierarchyEntityCount> topCounts = fetchTopEntityCounts(accountIdentifier, scope);
    log.info("{} top {} {} nodes by entity_count: {}", LOG_PREFIX, topCounts.size(), scope,
        topCounts.stream().map(c -> countKey(c, scope) + "=" + c.getCount()).collect(Collectors.toList()));

    Map<String, CatalogEntity> nodesByKey = fetchNodesByKey(accountIdentifier, scope, topCounts);

    List<ComparisonByHierarchyRow> rows = new ArrayList<>(topCounts.size());
    for (HierarchyEntityCount entityCount : topCounts) {
      String key = countKey(entityCount, scope);
      CatalogEntity node = nodesByKey.get(key);
      if (node == null) {
        log.info("{} no hierarchy node doc for key={} scope={}; skipping row", LOG_PREFIX, key, scope);
        continue;
      }
      rows.add(buildRow(accountIdentifier, node, scope, rules, entityCount.getCount()));
    }

    log.info("{} returning {} rows account={} scope={} in {} ms", LOG_PREFIX, rows.size(), accountIdentifier, scope,
        System.currentTimeMillis() - startMs);
    return new ComparisonByHierarchyCardDataResponse().columns(columns).rows(rows);
  }

  // ---- Validation ----------------------------------------------------------------------------

  private ComparisonByHierarchyScope validateRequestAndExtractScope(ComparisonByHierarchyCardDataRequest request) {
    if (request == null || request.getScope() == null) {
      throw new InvalidRequestException("scope is required");
    }
    return request.getScope();
  }

  /**
   * Ensures the persona view exists and references a card whose type is {@code COMPARISON_BY_HIERARCHY}.
   * Card identifiers can be OOTB ({@code ootb:*}, resolved under {@code __GLOBAL_ACCOUNT_ID__}) or
   * account-owned; {@link CardService#getCardsByIdentifiers(List, List)} handles both.
   */
  private void validatePersonaViewIncludesCard(String accountIdentifier, String personaViewIdentifier) {
    PersonaViewEntity entity =
        personaViewRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, personaViewIdentifier)
            .orElseThrow(() -> new NotFoundException(String.format(PERSONA_VIEW_NOT_FOUND, personaViewIdentifier)));

    List<String> cardIdentifiers = entity.getCards() == null ? List.of() : entity.getCards();
    if (cardIdentifiers.isEmpty()) {
      throw new NotFoundException(String.format(CARD_NOT_ON_VIEW, personaViewIdentifier));
    }
    List<Card> resolvedCards =
        cardService.getCardsByIdentifiers(List.of(accountIdentifier, GLOBAL_ACCOUNT_ID), cardIdentifiers);
    boolean hasCard = resolvedCards.stream().anyMatch(card -> Card.TypeEnum.COMPARISON_BY_HIERARCHY == card.getType());
    if (!hasCard) {
      throw new NotFoundException(String.format(CARD_NOT_ON_VIEW, personaViewIdentifier));
    }
  }

  // ---- Aggregation rule resolution -----------------------------------------------------------

  private List<AggregationRuleEntity> resolveAggregationRules(String accountIdentifier, List<String> ruleIds) {
    List<AggregationRuleEntity> allForAccount = aggregationRuleRepository.findByAccountIdentifier(accountIdentifier);
    if (isEmpty(ruleIds)) {
      return allForAccount.stream()
          .sorted(Comparator.comparingLong(AggregationRuleEntity::getCreatedAt))
          .limit(DEFAULT_RULE_LIMIT)
          .collect(Collectors.toList());
    }
    Map<String, AggregationRuleEntity> byIdentifier = allForAccount.stream().collect(
        Collectors.toMap(AggregationRuleEntity::getIdentifier, rule -> rule, (existing, ignored) -> existing));
    List<String> unknown = ruleIds.stream().filter(id -> !byIdentifier.containsKey(id)).collect(Collectors.toList());
    if (!unknown.isEmpty()) {
      throw new InvalidRequestException(String.format(UNKNOWN_AGGREGATION_RULES, unknown));
    }
    // Preserve caller-specified order.
    List<AggregationRuleEntity> ordered = new ArrayList<>(ruleIds.size());
    for (String id : ruleIds) {
      ordered.add(byIdentifier.get(id));
    }
    return ordered;
  }

  // ---- Top-N entity counts + node fetch (the ordering and entity_count column) ---------------

  /**
   * Top {@link #MAX_ROWS} nodes by entity count, ranked and limited inside Mongo. For ORG scope a node's
   * scope is everything under the Org (including entities in its Projects); for PROJECT scope it is the
   * entities in that Project. Structural hierarchy nodes are excluded from the count, so nodes with zero
   * entities never appear. Ordering is count desc, tie broken by the scope key ascending.
   */
  private List<HierarchyEntityCount> fetchTopEntityCounts(String accountIdentifier, ComparisonByHierarchyScope scope) {
    return scope == ComparisonByHierarchyScope.ORG
        ? catalogEntityRepository.topEntityCountsByOrg(accountIdentifier, HIERARCHY_KIND, MAX_ROWS)
        : catalogEntityRepository.topEntityCountsByOrgAndProject(accountIdentifier, HIERARCHY_KIND, MAX_ROWS);
  }

  /** Fetches the hierarchy node documents only for the ranked top-N keys, indexed by their scope key. */
  private Map<String, CatalogEntity> fetchNodesByKey(
      String accountIdentifier, ComparisonByHierarchyScope scope, List<HierarchyEntityCount> topCounts) {
    if (topCounts.isEmpty()) {
      return Collections.emptyMap();
    }
    List<CatalogEntity> nodes;
    if (scope == ComparisonByHierarchyScope.ORG) {
      List<String> orgIdentifiers =
          topCounts.stream().map(HierarchyEntityCount::getOrgIdentifier).collect(Collectors.toList());
      nodes = catalogEntityRepository.findHierarchyOrgNodes(accountIdentifier, HIERARCHY_KIND, orgIdentifiers);
    } else {
      nodes = catalogEntityRepository.findHierarchyProjectNodesForKeys(accountIdentifier, HIERARCHY_KIND, topCounts);
    }
    Map<String, CatalogEntity> byKey = new HashMap<>();
    for (CatalogEntity node : nodes) {
      byKey.put(nodeKey(node, scope), node);
    }
    return byKey;
  }

  private static String countKey(HierarchyEntityCount entityCount, ComparisonByHierarchyScope scope) {
    return scope == ComparisonByHierarchyScope.ORG
        ? entityCount.getOrgIdentifier()
        : entityCount.getOrgIdentifier() + "/" + entityCount.getProjectIdentifier();
  }

  private static String nodeKey(CatalogEntity node, ComparisonByHierarchyScope scope) {
    return scope == ComparisonByHierarchyScope.ORG ? node.getOrgIdentifier()
                                                   : node.getOrgIdentifier() + "/" + node.getProjectIdentifier();
  }

  // ---- Row assembly --------------------------------------------------------------------------

  private ComparisonByHierarchyRow buildRow(String accountIdentifier, CatalogEntity node,
      ComparisonByHierarchyScope scope, List<AggregationRuleEntity> rules, long entityCount) {
    ComparisonByHierarchyNode dtoNode =
        new ComparisonByHierarchyNode()
            .identifier(node.getIdentifier())
            .name(safeName(node.getName()))
            .type(scope == ComparisonByHierarchyScope.ORG ? ComparisonByHierarchyNode.TypeEnum.ORG
                                                          : ComparisonByHierarchyNode.TypeEnum.PROJECT);

    Map<String, BigDecimal> values = new LinkedHashMap<>();
    values.put(ENTITY_COUNT_COLUMN_ID, BigDecimal.valueOf(entityCount));
    Map<String, Object> metadata = extractMetadataBag(node);
    for (AggregationRuleEntity rule : rules) {
      values.put(rule.getIdentifier(), toBigDecimal(metadata.get(rule.getName())));
    }

    ScorecardComplianceCounts compliance = fetchCompliance(accountIdentifier, node, scope);

    return new ComparisonByHierarchyRow().node(dtoNode).values(values).scorecardCompliance(compliance);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> extractMetadataBag(CatalogEntity node) {
    Map<String, Object> processed = node.getFailSafeProcessedData();
    Object metadata = processed.get("metadata");
    return metadata instanceof Map ? (Map<String, Object>) metadata : Collections.emptyMap();
  }

  private static BigDecimal toBigDecimal(Object raw) {
    if (raw instanceof Number) {
      return BigDecimal.valueOf(((Number) raw).doubleValue());
    }
    if (raw instanceof String) {
      try {
        return new BigDecimal((String) raw);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static String safeName(String name) {
    return name == null ? "" : name;
  }

  // ---- Tier counts ---------------------------------------------------------------------------

  /**
   * Gold/Silver/Bronze counts for a node, served from a 10-minute TTL cache keyed by account + scope + node.
   * The cached value is independent of the requested aggregation-rule columns, so it is reused across loads.
   */
  private ScorecardComplianceCounts fetchCompliance(
      String accountIdentifier, CatalogEntity node, ComparisonByHierarchyScope scope) {
    String key = nodeKey(node, scope);
    ScorecardComplianceCache.Counts counts = complianceCache.getOrCompute(accountIdentifier, scope.name(), key, () -> {
      ScorecardComplianceCache.Counts computed = computeCompliance(accountIdentifier, node, scope);
      log.info("{} compliance cache MISS account={} scope={} node={} -> gold={} silver={} bronze={}", LOG_PREFIX,
          accountIdentifier, scope, key, computed.getGold(), computed.getSilver(), computed.getBronze());
      return computed;
    });
    return new ScorecardComplianceCounts().gold(counts.getGold()).silver(counts.getSilver()).bronze(counts.getBronze());
  }

  private ScorecardComplianceCache.Counts computeCompliance(
      String accountIdentifier, CatalogEntity node, ComparisonByHierarchyScope scope) {
    List<CatalogEntity> entitiesUnderNode = fetchEntitiesUnderNode(accountIdentifier, node, scope);
    if (entitiesUnderNode.isEmpty()) {
      return new ScorecardComplianceCache.Counts(0, 0, 0);
    }

    // Map entity identifier (the form ScoreEntity stores) -> CatalogEntity for downstream lookup.
    Map<String, CatalogEntity> byScoreEntityId = new HashMap<>();
    for (CatalogEntity entity : entitiesUnderNode) {
      byScoreEntityId.put(CatalogUtils.getEntityUUId(entity), entity);
    }
    List<String> entityIds = new ArrayList<>(byScoreEntityId.keySet());

    boolean idpV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    long computedAfter = System.currentTimeMillis() - SCORE_FRESHNESS_WINDOW_MILLIS;
    List<ScoreEntityByScorecardIdentifierEntityIdentifier> latestScores =
        scoreRepository
            .getAllLatestScoresByScorecardsForEntities(accountIdentifier, entityIds, idpV2Enabled, computedAfter)
            .getMappedResults();

    // Per-entity average score across all of its scorecards: [sum, count].
    Map<String, long[]> scoreSumCountByEntity = new HashMap<>();
    for (ScoreEntityByScorecardIdentifierEntityIdentifier projection : latestScores) {
      if (projection == null || projection.getScoreEntity() == null) {
        continue;
      }
      String entityId = projection.getEntityIdentifier();
      long[] sumCount = scoreSumCountByEntity.computeIfAbsent(entityId, key -> new long[2]);
      sumCount[0] += projection.getScoreEntity().getScore();
      sumCount[1]++;
    }

    int gold = 0;
    int silver = 0;
    int bronze = 0;
    for (long[] sumCount : scoreSumCountByEntity.values()) {
      if (sumCount[1] == 0) {
        continue;
      }
      // Compare the raw average against the tier thresholds (no rounding).
      double average = (double) sumCount[0] / sumCount[1];
      if (average >= GOLD_THRESHOLD) {
        gold++;
      } else if (average >= SILVER_THRESHOLD) {
        silver++;
      } else {
        bronze++;
      }
    }
    return new ScorecardComplianceCache.Counts(gold, silver, bronze);
  }

  private List<CatalogEntity> fetchEntitiesUnderNode(
      String accountIdentifier, CatalogEntity node, ComparisonByHierarchyScope scope) {
    String orgId = resolveOrgIdentifier(node);
    if (orgId == null) {
      return List.of();
    }
    if (scope == ComparisonByHierarchyScope.ORG) {
      return catalogEntityRepository.findEntitiesForOrgNode(accountIdentifier, orgId);
    }
    String projectId = node.getProjectIdentifier();
    if (projectId == null) {
      return List.of();
    }
    return catalogEntityRepository.findEntitiesForProjectNode(accountIdentifier, orgId, projectId);
  }

  /**
   * Hierarchy entities representing an Org carry {@code orgIdentifier} but not {@code projectIdentifier};
   * those representing a Project carry both. The org identifier is therefore present in either case.
   */
  private static String resolveOrgIdentifier(CatalogEntity node) {
    return node.getOrgIdentifier();
  }
}
