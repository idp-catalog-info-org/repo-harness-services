/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static lombok.AccessLevel.PRIVATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.integrations.repositories.IntegrationEntityRepository;
import io.harness.idp.integrations.service.IntegrationService;
import io.harness.idp.status.enums.StatusType;
import io.harness.idp.status.service.StatusInfoService;
import io.harness.migration.beans.NGMigration;
import io.harness.spec.server.idp.v1.model.StatusInfo;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IntegrationStatusInfoMigration implements NGMigration {
  @Inject IntegrationEntityRepository integrationEntityRepository;
  @Inject StatusInfoService statusInfoService;

  @Override
  public void migrate() {
    log.info("Starting the migration for Integration status info");
    List<String> accountIdentifiers = integrationEntityRepository.findUniqueAccountIdentifiers();
    log.info("Totally {} unique accounts present in Integration collection", accountIdentifiers);
    accountIdentifiers.forEach(accountIdentifier -> {
      try {
        statusInfoService.save(prepareStatusInfo(), accountIdentifier, StatusType.GIT_INTEGRATION.name());
        log.info("Integration status migrated successfully for account {}", accountIdentifier);
      } catch (Exception e) {
        log.error("Error in migrating integration status for account {}", accountIdentifier, e);
      }
    });
    log.info("Completed the migration for Integration status info");
  }

  private StatusInfo prepareStatusInfo() {
    StatusInfo statusInfo = new StatusInfo();
    statusInfo.setCurrentStatus(StatusInfo.CurrentStatusEnum.COMPLETED);
    statusInfo.setReason("Integration completed successfully");
    return statusInfo;
  }
}
