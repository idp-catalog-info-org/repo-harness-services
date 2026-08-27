/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.rule.OwnerRule.AVINASH_MADHWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.SettingUpdateType;
import io.harness.ngsettings.dto.SettingRequestDTO;
import io.harness.ngsettings.dto.SettingUpdateResponseDTO;
import io.harness.ngsettings.entities.AccountSetting;
import io.harness.ngsettings.services.SettingsService;
import io.harness.repositories.ngsettings.spring.SettingRepository;
import io.harness.rule.Owner;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

public class AidaSettingOptOutMigrationTest extends CategoryTest {
  private static final String RESOURCE_PATH = "io/harness/ngsettings/aida-optout-accounts.txt";

  @Mock private SettingsService settingsService;
  @Mock private SettingRepository settingRepository;
  @InjectMocks private AidaSettingOptOutMigration migration;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = AVINASH_MADHWANI)
  @Category(UnitTests.class)
  public void testMigrateReadsOptOutAccountsFromResourceAndSeedsAidaFalse() throws Exception {
    List<String> expectedAccountIds = loadAccountIdsFromResource();
    when(settingRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(
             any(), any(), eq(SettingIdentifiers.AIDA)))
        .thenReturn(Optional.empty());
    when(settingsService.update(any(ScopeInfo.class), anyList())).thenReturn(List.of(updateResponse(true)));

    migration.migrate();

    ArgumentCaptor<ScopeInfo> scopeCaptor = ArgumentCaptor.forClass(ScopeInfo.class);
    ArgumentCaptor<List<SettingRequestDTO>> requestCaptor = ArgumentCaptor.forClass(List.class);
    verify(settingsService, times(expectedAccountIds.size())).update(scopeCaptor.capture(), requestCaptor.capture());

    assertThat(scopeCaptor.getAllValues())
        .extracting(ScopeInfo::getAccountIdentifier)
        .containsExactlyElementsOf(expectedAccountIds);
    assertThat(scopeCaptor.getAllValues()).allSatisfy(scopeInfo -> {
      assertThat(scopeInfo.getUniqueId()).isEqualTo(scopeInfo.getAccountIdentifier());
      assertThat(scopeInfo.getScopeType().name()).isEqualTo("ACCOUNT");
    });
    requestCaptor.getAllValues().forEach(requests -> {
      assertThat(requests).hasSize(1);
      SettingRequestDTO request = requests.get(0);
      assertThat(request.getIdentifier()).isEqualTo(SettingIdentifiers.AIDA);
      assertThat(request.getValue()).isEqualTo("false");
      assertThat(request.getAllowOverrides()).isTrue();
      assertThat(request.getUpdateType()).isEqualTo(SettingUpdateType.UPDATE);
    });
  }

  @Test
  @Owner(developers = AVINASH_MADHWANI)
  @Category(UnitTests.class)
  public void testMigrateSkipsAccountsWithExistingAidaSetting() throws Exception {
    List<String> accountIds = loadAccountIdsFromResource();
    String existingAccountId = accountIds.get(0);
    when(settingRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(
             any(), any(), eq(SettingIdentifiers.AIDA)))
        .thenReturn(Optional.empty());
    when(settingRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(
             eq(existingAccountId), eq(existingAccountId), eq(SettingIdentifiers.AIDA)))
        .thenReturn(Optional.of(AccountSetting.builder().accountIdentifier(existingAccountId).build()));
    when(settingsService.update(any(ScopeInfo.class), anyList())).thenReturn(List.of(updateResponse(true)));

    migration.migrate();

    ArgumentCaptor<ScopeInfo> scopeCaptor = ArgumentCaptor.forClass(ScopeInfo.class);
    verify(settingsService, times(accountIds.size() - 1)).update(scopeCaptor.capture(), anyList());
    assertThat(scopeCaptor.getAllValues())
        .extracting(ScopeInfo::getAccountIdentifier)
        .doesNotContain(existingAccountId);
  }

  @Test
  @Owner(developers = AVINASH_MADHWANI)
  @Category(UnitTests.class)
  public void testMigrateCountsFailedUpdateResponsesAsFailures() throws Exception {
    List<String> accountIds = loadAccountIdsFromResource();
    String failedAccountId = accountIds.get(0);
    Logger logger = (Logger) LoggerFactory.getLogger(AidaSettingOptOutMigration.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
    when(settingRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(
             any(), any(), eq(SettingIdentifiers.AIDA)))
        .thenReturn(Optional.empty());
    when(settingsService.update(any(ScopeInfo.class), anyList())).thenAnswer(invocation -> {
      ScopeInfo scopeInfo = invocation.getArgument(0);
      return List.of(updateResponse(!failedAccountId.equals(scopeInfo.getAccountIdentifier())));
    });

    try {
      migration.migrate();
    } finally {
      logger.detachAppender(listAppender);
    }

    assertThat(listAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(message
            -> message.contains("inserted=" + (accountIds.size() - 1)) && message.contains("skipped=0")
                && message.contains("failed=1") && message.contains("total=" + accountIds.size()));
  }

  private List<String> loadAccountIdsFromResource() throws Exception {
    URL resource = getClass().getClassLoader().getResource(RESOURCE_PATH);
    assertThat(resource).isNotNull();
    try (BufferedReader reader =
             new BufferedReader(new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
      return reader.lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), accountIds -> {
            assertThat(accountIds).isNotEmpty();
            return List.copyOf(accountIds);
          }));
    }
  }

  private SettingUpdateResponseDTO updateResponse(boolean updateStatus) {
    return SettingUpdateResponseDTO.builder()
        .identifier(SettingIdentifiers.AIDA)
        .updateStatus(updateStatus)
        .errorMessage(updateStatus ? null : "failed")
        .build();
  }
}
