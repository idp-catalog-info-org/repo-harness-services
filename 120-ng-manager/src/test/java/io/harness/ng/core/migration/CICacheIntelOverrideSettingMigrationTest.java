/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.base.NgManagerTestBase;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.migration.background.CICacheIntelOverrideSettingMigration;
import io.harness.ngsettings.dto.SettingRequestDTO;
import io.harness.ngsettings.services.SettingsService;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class CICacheIntelOverrideSettingMigrationTest extends NgManagerTestBase {
  @Mock private NGFeatureFlagHelperService featureFlagService;
  @Mock private SettingsService settingsService;
  @InjectMocks private CICacheIntelOverrideSettingMigration ciCacheIntelOverrideSettingMigration;

  String accountId1 = "accountId1";
  String accountId2 = "accountId2";
  String accountId3 = "accountId3";
  Set<String> accountIds = ImmutableSet.of(accountId1, accountId2, accountId3);

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testCICacheIntelOverrideSettingMigration() {
    when(featureFlagService.getFeatureFlagEnabledAccountIds("CI_CACHE_OVERRIDE_FALSE")).thenReturn(accountIds);

    ciCacheIntelOverrideSettingMigration.migrate();

    ArgumentCaptor<ScopeInfo> scopeCaptor = ArgumentCaptor.forClass(ScopeInfo.class);
    ArgumentCaptor<List<SettingRequestDTO>> dtoCaptor = ArgumentCaptor.forClass(List.class);

    verify(settingsService, times(3)).update(scopeCaptor.capture(), dtoCaptor.capture());

    List<ScopeInfo> capturedScopes = scopeCaptor.getAllValues();
    List<List<SettingRequestDTO>> capturedDTOLists = dtoCaptor.getAllValues();

    assertThat(capturedScopes)
        .extracting(ScopeInfo::getAccountIdentifier)
        .containsExactlyInAnyOrder(accountId1, accountId2, accountId3);

    capturedDTOLists.forEach(dtoList -> {
      assertThat(dtoList).hasSize(1);
      SettingRequestDTO dto = dtoList.get(0);
      assertThat(dto.getIdentifier()).isEqualTo("ci_cache_intel_always_override");
      assertThat(dto.getValue()).isEqualTo("false");
      assertThat(dto.getAllowOverrides()).isTrue();
      assertThat(dto.getUpdateType().name()).isEqualTo("UPDATE");
    });
  }
}
