/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Adding migration script but will run only when there will be plan to enable failover

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class FailoverClusterNamespaceMigration implements NGMigration {
  K8sClient k8sClient;
  NamespaceService namespaceService;
  @Override
  public void migrate() {
    log.info("Starting migration for namespace creation in failover cluster");
    List<NamespaceEntity> activeAccounts = namespaceService.getActiveAccounts();

    for (NamespaceEntity account : activeAccounts) {
      k8sClient.createNamespaceForFailoverCluster(account.getId());
      log.info("Migration done for account - {} in failover cluster", account.getAccountIdentifier());
    }
    log.info("Migration for namespace creation in failover cluster is completed");
  }
}
