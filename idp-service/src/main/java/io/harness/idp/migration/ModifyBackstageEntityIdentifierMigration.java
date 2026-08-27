/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.service.BackstageService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.scorecard.checks.service.CheckService;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scores.service.ScoreService;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class ModifyBackstageEntityIdentifierMigration implements NGMigration {
  @Inject NamespaceService namespaceService;
  @Inject ScoreService scoreService;
  @Inject ScorecardService scorecardService;
  @Inject CheckService checkService;
  @Inject BackstageService backstageService;

  @Override
  public void migrate() {
    log.info("Starting the migration for modifying backstage entity identifiers.");
    List<String> accountIdentifiers = namespaceService.getAccountIds();
    log.info("Fetched {} IDP active accounts for modifying backstage entity identifier migration",
        accountIdentifiers.size());
    accountIdentifiers.forEach(accountIdentifier -> {
      backstageService.modifyEntityIdentifier(accountIdentifier);
      scoreService.modifyEntityIdentifier(accountIdentifier);
      scorecardService.modifyEntityIdentifier(accountIdentifier);
      checkService.modifyEntityIdentifier(accountIdentifier);
    });
    log.info("Completed the migration for modifying backstage entity identifiers.");
  }
}
