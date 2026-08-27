/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.NGConstants.HARNESS_ANTHROPIC_CONNECTOR_IDENTIFIER;
import static io.harness.NGConstants.HARNESS_OPENAI_CONNECTOR_IDENTIFIER;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.rule.OwnerRule.HIMANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.account.utils.AccountUtils;
import io.harness.base.NgManagerTestBase;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.embedded.anthropic.AnthropicConnector;
import io.harness.connector.entities.embedded.openai.OpenAIConnector;
import io.harness.ng.core.event.manager.HarnessLLMConnectorService;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

public class HarnessManagedLLMConnectorBackfillMigrationTest extends NgManagerTestBase {
  private static final String ACCOUNT_ID = "accountId";

  @Mock private AccountUtils accountUtils;
  @Mock private HarnessLLMConnectorService harnessLLMConnectorService;
  @Mock private MongoTemplate mongoTemplate;
  @InjectMocks private HarnessManagedLLMConnectorBackfillMigration migration;

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void migrateShouldProvisionMissingHarnessManagedLLMConnectorsForExistingAccounts() {
    when(accountUtils.getAllNGAccountIds()).thenReturn(List.of(ACCOUNT_ID));
    when(mongoTemplate.find(any(Query.class), eq(Connector.class))).thenReturn(List.of());
    when(harnessLLMConnectorService.createHarnessManagedLLMConnectors(eq(ACCOUNT_ID), eq(accountScopeInfo())))
        .thenAnswer(invocation -> {
          assertThat(SecurityContextBuilder.getPrincipal().getName()).isEqualTo(NG_MANAGER.getServiceId());
          assertThat(SourcePrincipalContextBuilder.getSourcePrincipal().getName()).isEqualTo(NG_MANAGER.getServiceId());
          return true;
        });

    migration.migrate();

    ArgumentCaptor<ScopeInfo> scopeInfoCaptor = ArgumentCaptor.forClass(ScopeInfo.class);
    verify(harnessLLMConnectorService, times(1))
        .createHarnessManagedLLMConnectors(eq(ACCOUNT_ID), scopeInfoCaptor.capture());
    assertThat(scopeInfoCaptor.getValue().getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(scopeInfoCaptor.getValue().getScopeType()).isEqualTo(ScopeLevel.ACCOUNT);
    assertThat(scopeInfoCaptor.getValue().getUniqueId()).isEqualTo(ACCOUNT_ID);
    assertThat(SecurityContextBuilder.getPrincipal()).isNull();
    assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isNull();
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void migrateShouldTryProvisioningWhenOnlyOneHarnessManagedLLMConnectorExists() {
    when(accountUtils.getAllNGAccountIds()).thenReturn(List.of(ACCOUNT_ID));
    when(mongoTemplate.find(any(Query.class), eq(Connector.class))).thenReturn(List.of(openAIConnector(true)));
    when(harnessLLMConnectorService.createHarnessManagedLLMConnectors(eq(ACCOUNT_ID), eq(accountScopeInfo())))
        .thenReturn(true);

    migration.migrate();

    verify(harnessLLMConnectorService, times(1))
        .createHarnessManagedLLMConnectors(eq(ACCOUNT_ID), eq(accountScopeInfo()));
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void migrateShouldNoopWhenHarnessManagedLLMConnectorsAlreadyExist() {
    when(accountUtils.getAllNGAccountIds()).thenReturn(List.of(ACCOUNT_ID));
    when(mongoTemplate.find(any(Query.class), eq(Connector.class)))
        .thenReturn(List.of(openAIConnector(true), anthropicConnector(true)));

    migration.migrate();

    verify(harnessLLMConnectorService, never()).createHarnessManagedLLMConnectors(any(), any());
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void migrateShouldNotThrowWhenHarnessManagedLLMConnectorCreationReturnsFailed() {
    when(accountUtils.getAllNGAccountIds()).thenReturn(List.of(ACCOUNT_ID));
    when(mongoTemplate.find(any(Query.class), eq(Connector.class))).thenReturn(List.of());
    when(harnessLLMConnectorService.createHarnessManagedLLMConnectors(eq(ACCOUNT_ID), eq(accountScopeInfo())))
        .thenReturn(false);

    assertThatCode(() -> migration.migrate()).doesNotThrowAnyException();

    verify(harnessLLMConnectorService, times(1))
        .createHarnessManagedLLMConnectors(eq(ACCOUNT_ID), eq(accountScopeInfo()));
  }

  private ScopeInfo accountScopeInfo() {
    return ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).scopeType(ScopeLevel.ACCOUNT).uniqueId(ACCOUNT_ID).build();
  }

  private OpenAIConnector openAIConnector(boolean harnessManagedLlm) {
    OpenAIConnector connector = OpenAIConnector.builder().harnessManagedLlm(harnessManagedLlm).build();
    connector.setIdentifier(HARNESS_OPENAI_CONNECTOR_IDENTIFIER);
    connector.setAccountIdentifier(ACCOUNT_ID);
    connector.setParentUniqueId(ACCOUNT_ID);
    return connector;
  }

  private AnthropicConnector anthropicConnector(boolean harnessManagedLlm) {
    AnthropicConnector connector = AnthropicConnector.builder().harnessManagedLlm(harnessManagedLlm).build();
    connector.setIdentifier(HARNESS_ANTHROPIC_CONNECTOR_IDENTIFIER);
    connector.setAccountIdentifier(ACCOUNT_ID);
    connector.setParentUniqueId(ACCOUNT_ID);
    return connector;
  }
}
