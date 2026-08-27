/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.NGConstants.HARNESS_ANTHROPIC_CONNECTOR_IDENTIFIER;
import static io.harness.NGConstants.HARNESS_OPENAI_CONNECTOR_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.AI;
import static io.harness.connector.entities.Connector.ConnectorKeys;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.embedded.anthropic.AnthropicConnector;
import io.harness.connector.entities.embedded.openai.OpenAIConnector;
import io.harness.migration.beans.NGMigration;
import io.harness.ng.core.event.manager.HarnessLLMConnectorService;
import io.harness.ng.core.security.NgManagerOpaContextGuard;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(AI)
@Slf4j
public class HarnessManagedLLMConnectorBackfillMigration implements NGMigration {
  private static final String LOG_PREFIX = "[HarnessManagedLLMConnectorBackfillMigration]";
  private static final List<String> HARNESS_MANAGED_LLM_CONNECTOR_IDENTIFIERS =
      List.of(HARNESS_OPENAI_CONNECTOR_IDENTIFIER, HARNESS_ANTHROPIC_CONNECTOR_IDENTIFIER);

  private final AccountUtils accountUtils;
  private final HarnessLLMConnectorService harnessLLMConnectorService;
  private final MongoTemplate mongoTemplate;

  @Inject
  public HarnessManagedLLMConnectorBackfillMigration(
      AccountUtils accountUtils, HarnessLLMConnectorService harnessLLMConnectorService, MongoTemplate mongoTemplate) {
    this.accountUtils = accountUtils;
    this.harnessLLMConnectorService = harnessLLMConnectorService;
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public void migrate() {
    List<String> accountIdentifiers = accountUtils.getAllNGAccountIds();
    if (isEmpty(accountIdentifiers)) {
      log.info("{} No accounts found for migration", LOG_PREFIX);
      return;
    }

    int successfulAccounts = 0;
    int failedAccounts = 0;
    log.info("{} Migration started for {} accounts", LOG_PREFIX, accountIdentifiers.size());

    for (String accountIdentifier : accountIdentifiers) {
      try {
        if (backfillAccount(accountIdentifier)) {
          successfulAccounts++;
        } else {
          failedAccounts++;
        }
      } catch (Exception e) {
        failedAccounts++;
        log.error("{} Failed to backfill account {}", LOG_PREFIX, accountIdentifier, e);
      }
    }

    log.info("{} Migration completed. successfulAccounts={}, failedAccounts={}", LOG_PREFIX, successfulAccounts,
        failedAccounts);
  }

  private boolean backfillAccount(String accountIdentifier) throws Exception {
    ScopeInfo accountScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(accountIdentifier)
                                     .scopeType(ScopeLevel.ACCOUNT)
                                     .uniqueId(accountIdentifier)
                                     .build();
    List<Connector> existingConnectors = fetchExistingAccountScopeLLMConnectors(accountIdentifier);

    boolean hasHarnessManagedOpenAIConnector = false;
    boolean hasHarnessManagedAnthropicConnector = false;
    for (Connector connector : existingConnectors) {
      if (HARNESS_OPENAI_CONNECTOR_IDENTIFIER.equals(connector.getIdentifier())) {
        hasHarnessManagedOpenAIConnector =
            hasHarnessManagedOpenAIConnector || isHarnessManagedOpenAIConnector(connector);
      } else if (HARNESS_ANTHROPIC_CONNECTOR_IDENTIFIER.equals(connector.getIdentifier())) {
        hasHarnessManagedAnthropicConnector =
            hasHarnessManagedAnthropicConnector || isHarnessManagedAnthropicConnector(connector);
      }
    }

    if (hasHarnessManagedOpenAIConnector && hasHarnessManagedAnthropicConnector) {
      log.info("{} Account {} already has Harness-managed LLM connectors", LOG_PREFIX, accountIdentifier);
      return true;
    }

    boolean connectorsProvisioned;
    try (NgManagerOpaContextGuard ignore = new NgManagerOpaContextGuard()) {
      connectorsProvisioned =
          harnessLLMConnectorService.createHarnessManagedLLMConnectors(accountIdentifier, accountScopeInfo);
    }
    if (!connectorsProvisioned) {
      log.error("{} Failed to provision Harness-managed LLM connectors for account {}", LOG_PREFIX, accountIdentifier);
      return false;
    }
    log.info("{} Provisioned Harness-managed LLM connectors for account {}", LOG_PREFIX, accountIdentifier);
    return true;
  }

  private List<Connector> fetchExistingAccountScopeLLMConnectors(String accountIdentifier) {
    Criteria criteria =
        where(ConnectorKeys.accountIdentifier)
            .is(accountIdentifier)
            .and(ConnectorKeys.parentUniqueId)
            .is(accountIdentifier)
            .and(ConnectorKeys.identifier)
            .in(HARNESS_MANAGED_LLM_CONNECTOR_IDENTIFIERS)
            .orOperator(where(ConnectorKeys.deleted).exists(false), where(ConnectorKeys.deleted).is(false));
    return mongoTemplate.find(new Query(criteria), Connector.class);
  }

  private boolean isHarnessManagedOpenAIConnector(Connector connector) {
    return connector instanceof OpenAIConnector
        && Boolean.TRUE.equals(((OpenAIConnector) connector).getHarnessManagedLlm());
  }

  private boolean isHarnessManagedAnthropicConnector(Connector connector) {
    return connector instanceof AnthropicConnector
        && Boolean.TRUE.equals(((AnthropicConnector) connector).getHarnessManagedLlm());
  }
}
