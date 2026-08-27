/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import io.harness.migration.beans.NGMigration;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.entities.AccountSetting.AccountSettingKeys;
import io.harness.ngsettings.entities.Setting.SettingKeys;
import io.harness.repositories.ngsettings.spring.SettingRepository;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
public class EnableSSCAAirgapSettingMigration implements NGMigration {
  @Inject SettingRepository settingRepository;

  public void migrate() {
    String settingIdentifier = SettingIdentifiers.ENABLE_SSCA_AIRGAP;
    try {
      Criteria criteria =
          Criteria.where(SettingKeys.identifier).is(settingIdentifier).and(AccountSettingKeys.value).is("false");
      Query query = new Query(criteria);

      Update update = new Update();
      update.set(AccountSettingKeys.value, "true");

      UpdateResult result = settingRepository.updateMultiple(query, update);

      log.info("[Migration Succeeded]: Successfully updated value of {} settings with identifier {}",
          result.getModifiedCount(), settingIdentifier);
    } catch (Exception ex) {
      log.error(
          "[Migration Failed]: Could not migrate the value for the setting with identifier, {}", settingIdentifier, ex);
    }
  }
}
