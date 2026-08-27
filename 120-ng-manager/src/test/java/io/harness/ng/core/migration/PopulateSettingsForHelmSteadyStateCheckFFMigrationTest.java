/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.base.NgManagerTestBase;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.migration.background.PopulateSettingsForHelmSteadyStateCheckFFMigration;
import io.harness.ngsettings.entities.AccountSetting;
import io.harness.repositories.ngsettings.spring.SettingRepository;
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

public class PopulateSettingsForHelmSteadyStateCheckFFMigrationTest extends NgManagerTestBase {
  @Mock private NGFeatureFlagHelperService featureFlagService;
  @Mock private SettingRepository settingRepository;
  @InjectMocks
  private PopulateSettingsForHelmSteadyStateCheckFFMigration populateSettingsForHelmSteadyStateCheckFFMigration;
  String accountId1 = "accountId1";
  String accountId2 = "accountId2";
  String accountId3 = "accountId3";
  Set<String> accountIds = ImmutableSet.of(accountId1, accountId2, accountId3);

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testPopulateSettingsForHelmSteadyStateCheckFFMigration() {
    ArgumentCaptor<AccountSetting> accountSettingCaptor = ArgumentCaptor.forClass(AccountSetting.class);
    when(featureFlagService.getFeatureFlagEnabledAccountIds(FeatureName.HELM_STEADY_STATE_CHECK_1_16.name()))
        .thenReturn(accountIds);
    populateSettingsForHelmSteadyStateCheckFFMigration.migrate();
    verify(settingRepository, atLeast(3)).upsert(accountSettingCaptor.capture());
    List<AccountSetting> capturedSettings = accountSettingCaptor.getAllValues();
    assertThat(capturedSettings)
        .extracting(AccountSetting::getAccountIdentifier)
        .contains(accountId1, accountId2, accountId3);
  }
}
