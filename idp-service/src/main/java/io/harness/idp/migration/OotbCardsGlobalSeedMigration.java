/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.idp.personaview.PersonaViewConstants.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.homepage.entities.CardEntity;
import io.harness.idp.homepage.entities.ComparisonByHierarchyCardEntity;
import io.harness.idp.homepage.entities.EntityDistributionOwnershipCardEntity;
import io.harness.idp.homepage.entities.IncidentTrendCardEntity;
import io.harness.idp.homepage.entities.IncidentsCardEntity;
import io.harness.idp.homepage.entities.IntegrationsCardEntity;
import io.harness.idp.homepage.entities.RecentBuildsCardEntity;
import io.harness.idp.homepage.entities.RecentDeploymentsCardEntity;
import io.harness.idp.homepage.entities.ScorecardComplianceCardEntity;
import io.harness.idp.homepage.entities.SecurityFindingsCardEntity;
import io.harness.idp.homepage.entities.StoCardEntity;
import io.harness.idp.homepage.entities.TopFailingChecksCardEntity;
import io.harness.idp.homepage.entities.WorkflowsEnvironmentsCardEntity;
import io.harness.idp.homepage.repositories.CardRepository;
import io.harness.migration.beans.NGMigration;
import io.harness.spec.server.idp.v1.model.Card;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds the 12 OOTB {@link CardEntity} rows under the reserved {@link
 * io.harness.idp.personaview.PersonaViewConstants#GLOBAL_ACCOUNT_ID} account. Runs once globally. Re-runs are
 * idempotent — any OOTB identifier already present is skipped.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class OotbCardsGlobalSeedMigration implements NGMigration {
  private static final String LOG_PREFIX = "[OotbCardsGlobalSeedMigration]";
  private static final String OOTB_ICON_URL = "";

  @Inject private CardRepository cardRepository;

  @Override
  public void migrate() {
    log.info("{} Starting OOTB cards global seed migration", LOG_PREFIX);

    List<CardEntity> ootbCards = buildOotbCards();
    int created = 0;
    int skipped = 0;
    for (CardEntity card : ootbCards) {
      if (cardRepository.findByAccountIdentifierAndIdentifier(GLOBAL_ACCOUNT_ID, card.getIdentifier()).isPresent()) {
        skipped++;
        continue;
      }
      try {
        cardRepository.save(card);
        created++;
      } catch (Exception ex) {
        log.warn("{} Failed to seed OOTB card {} - {}", LOG_PREFIX, card.getIdentifier(), ex.getMessage(), ex);
      }
    }
    log.info("{} Done. created={} skipped={} total={}", LOG_PREFIX, created, skipped, ootbCards.size());
  }

  private List<CardEntity> buildOotbCards() {
    List<CardEntity> cards = new ArrayList<>();

    // Homepage v2 (medium)
    cards.add(decorate(
        IncidentsCardEntity.builder().size("medium").build(), "ootb:incidents", "Incidents", Card.TypeEnum.INCIDENTS));
    cards.add(decorate(RecentBuildsCardEntity.builder().size("medium").build(), "ootb:recent-builds", "Recent Builds",
        Card.TypeEnum.RECENT_BUILDS));
    cards.add(decorate(RecentDeploymentsCardEntity.builder().size("medium").build(), "ootb:recent-deployments",
        "Recent Deployments", Card.TypeEnum.RECENT_DEPLOYMENTS));

    // Shared (PLATFORM + LEADERSHIP)
    cards.add(decorate(EntityDistributionOwnershipCardEntity.builder().size("large").build(),
        "ootb:entity-distribution-ownership", "Entity ownership distribution",
        Card.TypeEnum.ENTITY_DISTRIBUTION_OWNERSHIP));
    cards.add(decorate(ScorecardComplianceCardEntity.builder().size("medium").build(), "ootb:scorecard-compliance",
        "Scorecard compliance", Card.TypeEnum.SCORECARD_COMPLIANCE));

    // PLATFORM only
    cards.add(decorate(TopFailingChecksCardEntity.builder().size("medium").build(), "ootb:top-failing-checks",
        "Top failing checks", Card.TypeEnum.TOP_FAILING_CHECKS));
    cards.add(decorate(WorkflowsEnvironmentsCardEntity.builder().size("medium").build(), "ootb:workflows-environments",
        "Workflows by environment", Card.TypeEnum.WORKFLOWS_ENVIRONMENTS));
    cards.add(decorate(IntegrationsCardEntity.builder().size("medium").build(), "ootb:integrations", "Integrations",
        Card.TypeEnum.INTEGRATIONS));

    // LEADERSHIP only
    cards.add(decorate(IncidentTrendCardEntity.builder().size("medium").build(), "ootb:incident-trend",
        "Incident trend", Card.TypeEnum.INCIDENT_TREND));
    cards.add(decorate(SecurityFindingsCardEntity.builder().size("medium").build(), "ootb:security-findings",
        "Security findings", Card.TypeEnum.SECURITY_FINDINGS));
    cards.add(
        decorate(StoCardEntity.builder().size("medium").build(), "ootb:sto", "Security testing", Card.TypeEnum.STO));
    cards.add(decorate(ComparisonByHierarchyCardEntity.builder().size("large").build(), "ootb:comparison-by-hierarchy",
        "Comparison by hierarchy", Card.TypeEnum.COMPARISON_BY_HIERARCHY));

    return cards;
  }

  private CardEntity decorate(CardEntity entity, String identifier, String title, Card.TypeEnum type) {
    entity.setAccountIdentifier(GLOBAL_ACCOUNT_ID);
    entity.setIdentifier(identifier);
    entity.setTitle(title);
    entity.setIsDefault(true);
    entity.setIsDraft(false);
    entity.setType(type);
    entity.setIconUrl(OOTB_ICON_URL);
    return entity;
  }
}
