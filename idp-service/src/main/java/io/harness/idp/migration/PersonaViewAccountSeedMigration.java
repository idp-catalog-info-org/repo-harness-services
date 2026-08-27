/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.personaview.service.PersonaViewService;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * One-shot <b>catch-up</b> backfill of OOTB persona view rows ({@code platform}, {@code leadership}) for
 * accounts that were provisioned for IDP <em>before</em> {@link
 * io.harness.idp.provision.service.ProvisionServiceImpl#provision} started seeding them. Steady-state seeding
 * lives in {@code ProvisionServiceImpl}; this migration exists solely to bring pre-existing accounts up to the
 * new baseline.
 *
 * <p>Delegates to {@link PersonaViewService#seedOotbPersonaViewsIfNotAlready(String)} for every IDP-active
 * account so that the seed source-of-truth (the JSON resource loaded by the service) remains the only place
 * OOTB view definitions live. Idempotent and race-safe via the unique index on
 * {@code (accountIdentifier, identifier)}.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class PersonaViewAccountSeedMigration implements NGMigration {
  private static final String LOG_PREFIX = "[PersonaViewAccountSeedMigration]";

  @Inject private NamespaceService namespaceService;
  @Inject private PersonaViewService personaViewService;

  @Override
  public void migrate() {
    log.info("{} Starting catch-up seed of OOTB persona views for existing IDP accounts", LOG_PREFIX);

    List<String> idpAccounts = namespaceService.getAccountIds();
    int processed = 0;
    int failed = 0;
    for (String accountIdentifier : idpAccounts) {
      try {
        personaViewService.seedOotbPersonaViewsIfNotAlready(accountIdentifier);
        processed++;
      } catch (Exception ex) {
        failed++;
        log.warn("{} Error seeding OOTB persona views for account {} - {}", LOG_PREFIX, accountIdentifier,
            ex.getMessage(), ex);
      }
    }
    log.info("{} Done. processed={} failed={} totalAccounts={}", LOG_PREFIX, processed, failed, idpAccounts.size());
  }
}
