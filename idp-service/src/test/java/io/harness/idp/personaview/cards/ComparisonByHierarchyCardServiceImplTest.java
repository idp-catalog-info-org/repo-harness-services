/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.personaview.cards;

import static io.harness.idp.catalog.utils.Constants.HIERARCHY_KIND;
import static io.harness.rule.OwnerRule.HARJAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.idp.aggregation.rules.repositories.AggregationRuleRepository;
import io.harness.idp.catalog.beans.HierarchyEntityCount;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.homepage.service.CardService;
import io.harness.idp.personaview.entities.PersonaViewEntity;
import io.harness.idp.personaview.repositories.PersonaViewRepository;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.idp.scorecard.scores.repositories.ScoreEntityByScorecardIdentifierEntityIdentifier;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.Card;
import io.harness.spec.server.idp.v1.model.ComparisonByHierarchyCardDataRequest;
import io.harness.spec.server.idp.v1.model.ComparisonByHierarchyCardDataResponse;
import io.harness.spec.server.idp.v1.model.ComparisonByHierarchyNode;
import io.harness.spec.server.idp.v1.model.ComparisonByHierarchyScope;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@OwnedBy(HarnessTeam.IDP)
public class ComparisonByHierarchyCardServiceImplTest extends CategoryTest {
  private static final String ACCOUNT = "acc-1";
  private static final String VIEW = "leadership";
  private static final String COMPARISON_CARD_ID = "ootb:comparison-by-hierarchy";
  private static final String OTHER_CARD_ID = "ootb:scorecard-compliance";

  @Mock private PersonaViewRepository personaViewRepository;
  @Mock private CardService cardService;
  @Mock private AggregationRuleRepository aggregationRuleRepository;
  @Mock private CatalogEntityRepository catalogEntityRepository;
  @Mock private ScoreRepository scoreRepository;
  @Mock private IdpCommonService idpCommonService;
  @Mock private AggregationResults<ScoreEntityByScorecardIdentifierEntityIdentifier> emptyScoresAgg;

  private ComparisonByHierarchyCardServiceImpl service;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    service = new ComparisonByHierarchyCardServiceImpl(personaViewRepository, cardService, aggregationRuleRepository,
        catalogEntityRepository, scoreRepository, idpCommonService, new ScorecardComplianceCache());
    when(idpCommonService.idpV2Enabled(ACCOUNT)).thenReturn(true);
    when(emptyScoresAgg.getMappedResults()).thenReturn(List.of());
    when(scoreRepository.getAllLatestScoresByScorecardsForEntities(anyString(), anyList(), eq(true), anyLong()))
        .thenReturn(emptyScoresAgg);
    when(catalogEntityRepository.topEntityCountsByOrg(anyString(), anyString(), anyInt())).thenReturn(List.of());
    when(catalogEntityRepository.topEntityCountsByOrgAndProject(anyString(), anyString(), anyInt()))
        .thenReturn(List.of());
    when(catalogEntityRepository.findHierarchyOrgNodes(anyString(), anyString(), anyList())).thenReturn(List.of());
    when(catalogEntityRepository.findHierarchyProjectNodesForKeys(anyString(), anyString(), anyList()))
        .thenReturn(List.of());
    stubViewWithCard();
  }

  // ---- Validation ---------------------------------------------------------------------------

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void scopeIsRequired() {
    assertThatThrownBy(() -> service.getData(ACCOUNT, VIEW, new ComparisonByHierarchyCardDataRequest()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("scope");
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void unknownPersonaView404() {
    when(personaViewRepository.findByAccountIdentifierAndIdentifier(ACCOUNT, "missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getData(ACCOUNT, "missing", orgRequest()))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("missing");
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void viewWithoutCard404() {
    when(cardService.getCardsByIdentifiers(anyList(), anyList()))
        .thenReturn(List.of(card(OTHER_CARD_ID, Card.TypeEnum.SCORECARD_COMPLIANCE)));
    assertThatThrownBy(() -> service.getData(ACCOUNT, VIEW, orgRequest()))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(VIEW);
  }

  // ---- Aggregation rule resolution ----------------------------------------------------------

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void emptyRuleIdsDefaultsToFirstFiveByCreatedAt() {
    // 7 rules, oldest first. Default should pick the 5 oldest.
    List<AggregationRuleEntity> rules =
        List.of(rule("r1", "Rule 1", 1), rule("r2", "Rule 2", 2), rule("r3", "Rule 3", 3), rule("r4", "Rule 4", 4),
            rule("r5", "Rule 5", 5), rule("r6", "Rule 6", 6), rule("r7", "Rule 7", 7));
    when(aggregationRuleRepository.findByAccountIdentifier(ACCOUNT)).thenReturn(rules);

    ComparisonByHierarchyCardDataResponse response = service.getData(ACCOUNT, VIEW, orgRequest());
    // entity_count is always the leading column, followed by the resolved rule columns.
    assertThat(response.getColumns()).extracting("id").containsExactly("entity_count", "r1", "r2", "r3", "r4", "r5");
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void unknownRuleId400ListsOffenders() {
    when(aggregationRuleRepository.findByAccountIdentifier(ACCOUNT)).thenReturn(List.of(rule("r1", "Rule 1", 1)));

    ComparisonByHierarchyCardDataRequest req = orgRequest();
    req.setAggregationRuleIds(List.of("r1", "missing-1", "missing-2"));

    assertThatThrownBy(() -> service.getData(ACCOUNT, VIEW, req))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("missing-1")
        .hasMessageContaining("missing-2");
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void explicitRuleIdsPreserveRequestOrder() {
    when(aggregationRuleRepository.findByAccountIdentifier(ACCOUNT))
        .thenReturn(List.of(rule("r1", "Rule 1", 1), rule("r2", "Rule 2", 2), rule("r3", "Rule 3", 3)));

    ComparisonByHierarchyCardDataRequest req = orgRequest();
    req.setAggregationRuleIds(List.of("r3", "r1"));

    ComparisonByHierarchyCardDataResponse response = service.getData(ACCOUNT, VIEW, req);
    assertThat(response.getColumns()).extracting("id").containsExactly("entity_count", "r3", "r1");
  }

  // ---- ORG scope rows -----------------------------------------------------------------------

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void orgScopeRowsCarryMetadataAndNullForMissingKeys() {
    AggregationRuleEntity ruleA = rule("rA", "deploy_freq", 1);
    AggregationRuleEntity ruleB = rule("rB", "mttr", 2);
    when(aggregationRuleRepository.findByAccountIdentifier(ACCOUNT)).thenReturn(List.of(ruleA, ruleB));

    CatalogEntity orgEng = hierarchyEntity("engineering", "engineering", null, Map.of("deploy_freq", 12.5));
    CatalogEntity orgOps = hierarchyEntity("ops", "ops", null, Map.of("mttr", 30));
    // Aggregation already ranks engineering (5) before ops (3); service preserves that order.
    stubOrgScope(List.of(orgCount("engineering", 5), orgCount("ops", 3)), List.of(orgEng, orgOps));

    ComparisonByHierarchyCardDataResponse response = service.getData(ACCOUNT, VIEW, orgRequest());

    assertThat(response.getRows()).hasSize(2);
    assertThat(response.getRows().get(0).getNode().getType()).isEqualTo(ComparisonByHierarchyNode.TypeEnum.ORG);
    assertThat(response.getRows().get(0).getNode().getIdentifier()).isEqualTo("engineering");
    assertThat(response.getRows().get(0).getValues().get("entity_count")).isEqualByComparingTo(BigDecimal.valueOf(5));
    assertThat(response.getRows().get(0).getValues().get("rA")).isEqualByComparingTo(BigDecimal.valueOf(12.5));
    assertThat(response.getRows().get(0).getValues().get("rB")).isNull();
    assertThat(response.getRows().get(1).getNode().getIdentifier()).isEqualTo("ops");
    assertThat(response.getRows().get(1).getValues().get("entity_count")).isEqualByComparingTo(BigDecimal.valueOf(3));
    assertThat(response.getRows().get(1).getValues().get("rA")).isNull();
    assertThat(response.getRows().get(1).getValues().get("rB")).isEqualByComparingTo(BigDecimal.valueOf(30));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void rowsFollowAggregationOrderByEntityCountDesc() {
    when(aggregationRuleRepository.findByAccountIdentifier(ACCOUNT)).thenReturn(List.of(rule("r1", "Rule 1", 1)));
    // The aggregation returns the already-ranked top 5 (count desc); the service must preserve that order.
    stubOrgScope(List.of(orgCount("g", 7), orgCount("f", 6), orgCount("e", 5), orgCount("d", 4), orgCount("c", 3)),
        List.of(hierarchyEntity("g", "g", null, Map.of()), hierarchyEntity("f", "f", null, Map.of()),
            hierarchyEntity("e", "e", null, Map.of()), hierarchyEntity("d", "d", null, Map.of()),
            hierarchyEntity("c", "c", null, Map.of())));

    ComparisonByHierarchyCardDataResponse response = service.getData(ACCOUNT, VIEW, orgRequest());
    assertThat(response.getRows()).hasSize(5);
    assertThat(response.getRows())
        .extracting(row -> row.getNode().getIdentifier())
        .containsExactly("g", "f", "e", "d", "c");
    assertThat(response.getRows().get(0).getValues().get("entity_count")).isEqualByComparingTo(BigDecimal.valueOf(7));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void countKeyWithoutMatchingNodeDocIsSkipped() {
    when(aggregationRuleRepository.findByAccountIdentifier(ACCOUNT)).thenReturn(List.of(rule("r1", "Rule 1", 1)));
    // Aggregation reports an org, but no hierarchy node doc exists for it -> row is skipped.
    stubOrgScope(List.of(orgCount("ghost", 3)), List.of());

    ComparisonByHierarchyCardDataResponse response = service.getData(ACCOUNT, VIEW, orgRequest());
    assertThat(response.getRows()).isEmpty();
  }

  // ---- PROJECT scope rows -------------------------------------------------------------------

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void projectScopeRowsOrderedByEntityCountAndNeverUseOrgQueries() {
    when(aggregationRuleRepository.findByAccountIdentifier(ACCOUNT)).thenReturn(List.of(rule("r1", "Rule 1", 1)));

    CatalogEntity billing = hierarchyEntity("billing", "engineering", "billing", Map.of());
    CatalogEntity payments = hierarchyEntity("payments", "engineering", "payments", Map.of());
    // billing (9) ranks before payments (2).
    stubProjectScope(List.of(projectCount("engineering", "billing", 9), projectCount("engineering", "payments", 2)),
        List.of(billing, payments));

    ComparisonByHierarchyCardDataRequest req = new ComparisonByHierarchyCardDataRequest();
    req.setScope(ComparisonByHierarchyScope.PROJECT);

    ComparisonByHierarchyCardDataResponse response = service.getData(ACCOUNT, VIEW, req);

    assertThat(response.getRows()).hasSize(2);
    assertThat(response.getRows())
        .allMatch(row -> row.getNode().getType() == ComparisonByHierarchyNode.TypeEnum.PROJECT);
    assertThat(response.getRows())
        .extracting(row -> row.getNode().getIdentifier())
        .containsExactly("billing", "payments");
    assertThat(response.getRows().get(0).getValues().get("entity_count")).isEqualByComparingTo(BigDecimal.valueOf(9));
    // ORG-scope queries must not be touched on the PROJECT path.
    verify(catalogEntityRepository, never()).topEntityCountsByOrg(anyString(), anyString(), anyInt());
    verify(catalogEntityRepository, never()).findHierarchyOrgNodes(anyString(), anyString(), anyList());
  }

  // ---- Tier bucketing -----------------------------------------------------------------------

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void tierBucketingUsesAverageScorePerEntity() {
    when(aggregationRuleRepository.findByAccountIdentifier(ACCOUNT)).thenReturn(List.of(rule("r1", "Rule 1", 1)));
    CatalogEntity org = hierarchyEntity("engineering", "engineering", null, Map.of());
    stubOrgScope(List.of(orgCount("engineering", 3)), List.of(org));

    // Three service-like entities under the org.
    CatalogEntity svc1 = serviceEntity("svc-a", "engineering", null);
    CatalogEntity svc2 = serviceEntity("svc-b", "engineering", null);
    CatalogEntity svc3 = serviceEntity("svc-c", "engineering", null);
    when(catalogEntityRepository.findEntitiesForOrgNode(eq(ACCOUNT), eq("engineering")))
        .thenReturn(List.of(svc1, svc2, svc3));

    // Tier comes from the per-entity AVERAGE (no rounding): GOLD >= 75, SILVER >= 50, else bronze.
    // svc-a: avg(40, 80) = 60 -> silver (best-score would have been 80 = gold; averaging pulls it down).
    // svc-b: 100 -> gold. svc-c: 30 -> bronze.
    AggregationResults<ScoreEntityByScorecardIdentifierEntityIdentifier> agg =
        mockAggregation(List.of(scoreRow(uuid(svc1), "sc1", 40), scoreRow(uuid(svc1), "sc2", 80),
            scoreRow(uuid(svc2), "sc1", 100), scoreRow(uuid(svc3), "sc1", 30)));
    when(scoreRepository.getAllLatestScoresByScorecardsForEntities(eq(ACCOUNT), anyList(), eq(true), anyLong()))
        .thenReturn(agg);

    ComparisonByHierarchyCardDataResponse response = service.getData(ACCOUNT, VIEW, orgRequest());
    assertThat(response.getRows()).hasSize(1);
    assertThat(response.getRows().get(0).getScorecardCompliance().getGold()).isEqualTo(1);
    assertThat(response.getRows().get(0).getScorecardCompliance().getSilver()).isEqualTo(1);
    assertThat(response.getRows().get(0).getScorecardCompliance().getBronze()).isEqualTo(1);
  }

  // ---- Compliance cache ---------------------------------------------------------------------

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void complianceCacheServesRepeatLoadsWithoutReQuerying() {
    when(aggregationRuleRepository.findByAccountIdentifier(ACCOUNT)).thenReturn(List.of(rule("r1", "Rule 1", 1)));
    CatalogEntity org = hierarchyEntity("engineering", "engineering", null, Map.of());
    stubOrgScope(List.of(orgCount("engineering", 4)), List.of(org));

    CatalogEntity svc1 = serviceEntity("svc-a", "engineering", null);
    when(catalogEntityRepository.findEntitiesForOrgNode(eq(ACCOUNT), eq("engineering"))).thenReturn(List.of(svc1));
    AggregationResults<ScoreEntityByScorecardIdentifierEntityIdentifier> agg =
        mockAggregation(List.of(scoreRow(uuid(svc1), "sc1", 92)));
    when(scoreRepository.getAllLatestScoresByScorecardsForEntities(eq(ACCOUNT), anyList(), eq(true), anyLong()))
        .thenReturn(agg);

    ComparisonByHierarchyCardDataResponse first = service.getData(ACCOUNT, VIEW, orgRequest());
    ComparisonByHierarchyCardDataResponse second = service.getData(ACCOUNT, VIEW, orgRequest());

    assertThat(first.getRows().get(0).getScorecardCompliance().getGold()).isEqualTo(1);
    assertThat(second.getRows().get(0).getScorecardCompliance().getGold()).isEqualTo(1);
    // The expensive score aggregation runs once; the second load is served from the cache.
    verify(scoreRepository, times(1))
        .getAllLatestScoresByScorecardsForEntities(eq(ACCOUNT), anyList(), eq(true), anyLong());
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void distinctNodesComputeComplianceSeparately() {
    when(aggregationRuleRepository.findByAccountIdentifier(ACCOUNT)).thenReturn(List.of(rule("r1", "Rule 1", 1)));
    CatalogEntity orgEng = hierarchyEntity("engineering", "engineering", null, Map.of());
    CatalogEntity orgOps = hierarchyEntity("ops", "ops", null, Map.of());
    stubOrgScope(List.of(orgCount("engineering", 5), orgCount("ops", 3)), List.of(orgEng, orgOps));

    when(catalogEntityRepository.findEntitiesForOrgNode(eq(ACCOUNT), anyString()))
        .thenReturn(List.of(serviceEntity("svc-a", "engineering", null)));

    service.getData(ACCOUNT, VIEW, orgRequest());
    // Two distinct node keys -> two separate score aggregations within a single request.
    verify(scoreRepository, times(2))
        .getAllLatestScoresByScorecardsForEntities(eq(ACCOUNT), anyList(), eq(true), anyLong());
  }

  // ---- helpers ------------------------------------------------------------------------------

  private void stubViewWithCard() {
    PersonaViewEntity view = PersonaViewEntity.builder()
                                 .accountIdentifier(ACCOUNT)
                                 .identifier(VIEW)
                                 .name("Leadership")
                                 .cards(List.of(COMPARISON_CARD_ID))
                                 .build();
    when(personaViewRepository.findByAccountIdentifierAndIdentifier(ACCOUNT, VIEW)).thenReturn(Optional.of(view));
    when(cardService.getCardsByIdentifiers(anyList(), anyList()))
        .thenReturn(List.of(card(COMPARISON_CARD_ID, Card.TypeEnum.COMPARISON_BY_HIERARCHY)));
  }

  private void stubOrgScope(List<HierarchyEntityCount> counts, List<CatalogEntity> nodes) {
    when(catalogEntityRepository.topEntityCountsByOrg(eq(ACCOUNT), eq(HIERARCHY_KIND), anyInt())).thenReturn(counts);
    when(catalogEntityRepository.findHierarchyOrgNodes(eq(ACCOUNT), eq(HIERARCHY_KIND), anyList())).thenReturn(nodes);
  }

  private void stubProjectScope(List<HierarchyEntityCount> counts, List<CatalogEntity> nodes) {
    when(catalogEntityRepository.topEntityCountsByOrgAndProject(eq(ACCOUNT), eq(HIERARCHY_KIND), anyInt()))
        .thenReturn(counts);
    when(catalogEntityRepository.findHierarchyProjectNodesForKeys(eq(ACCOUNT), eq(HIERARCHY_KIND), anyList()))
        .thenReturn(nodes);
  }

  private static HierarchyEntityCount orgCount(String orgIdentifier, long count) {
    return HierarchyEntityCount.builder().orgIdentifier(orgIdentifier).count(count).build();
  }

  private static HierarchyEntityCount projectCount(String orgIdentifier, String projectIdentifier, long count) {
    return HierarchyEntityCount.builder()
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .count(count)
        .build();
  }

  private static ComparisonByHierarchyCardDataRequest orgRequest() {
    ComparisonByHierarchyCardDataRequest req = new ComparisonByHierarchyCardDataRequest();
    req.setScope(ComparisonByHierarchyScope.ORG);
    return req;
  }

  private static AggregationRuleEntity rule(String identifier, String name, long createdAt) {
    return AggregationRuleEntity.builder()
        .identifier(identifier)
        .name(name)
        .accountIdentifier(ACCOUNT)
        .createdAt(createdAt)
        .build();
  }

  private static Card card(String identifier, Card.TypeEnum type) {
    Card card = new Card();
    card.setIdentifier(identifier);
    card.setType(type);
    return card;
  }

  /**
   * Hierarchy catalog entity with the decorator metadata populated so that
   * {@code decorator._processed_data.metadata} returns {@code metadata}.
   */
  private static CatalogEntity hierarchyEntity(
      String identifier, String orgIdentifier, String projectIdentifier, Map<String, Object> metadata) {
    Map<String, Object> processed = new HashMap<>();
    processed.put("metadata", new HashMap<>(metadata));
    Map<String, Object> decorator = new HashMap<>();
    decorator.put("_processed_data", processed);
    return InlineCatalogEntity.builder()
        .identifier(identifier)
        .name(identifier)
        .kind(HIERARCHY_KIND)
        .accountIdentifier(ACCOUNT)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .decorator(decorator)
        .build();
  }

  private static CatalogEntity serviceEntity(String identifier, String orgIdentifier, String projectIdentifier) {
    return InlineCatalogEntity.builder()
        .identifier(identifier)
        .name(identifier)
        .kind("component")
        .accountIdentifier(ACCOUNT)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private static String uuid(CatalogEntity entity) {
    return io.harness.idp.catalog.utils.CatalogUtils.getEntityUUId(entity);
  }

  private static ScoreEntityByScorecardIdentifierEntityIdentifier scoreRow(
      String entityId, String scorecardId, int score) {
    return ScoreEntityByScorecardIdentifierEntityIdentifier.builder()
        .scorecardIdentifier(scorecardId)
        .entityIdentifier(entityId)
        .scoreEntity(
            ScoreEntity.builder().entityIdentifier(entityId).scorecardIdentifier(scorecardId).score(score).build())
        .build();
  }

  @SuppressWarnings("unchecked")
  private static AggregationResults<ScoreEntityByScorecardIdentifierEntityIdentifier> mockAggregation(
      List<ScoreEntityByScorecardIdentifierEntityIdentifier> rows) {
    AggregationResults<ScoreEntityByScorecardIdentifierEntityIdentifier> agg =
        org.mockito.Mockito.mock(AggregationResults.class);
    when(agg.getMappedResults()).thenReturn(rows);
    return agg;
  }
}
