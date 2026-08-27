/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.costSettings.CostSettings;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.ng.core.entities.ApiKey;
import io.harness.ng.core.entities.Token;
import io.harness.ng.core.entities.metrics.AccountStatisticsTracker;
import io.harness.ng.core.entities.migration.NGManagerCDCEntitiesMigrationStatus;
import io.harness.ng.core.entities.migration.NGManagerUniqueIdParentIdMigrationStatus;
import io.harness.ng.core.entities.migration.NgManagerTsdbUniqueIdParentIdMigrationStatus;
import io.harness.ng.core.entities.migration.RecomputeParentUniqueIdMigrationStatus;
import io.harness.ng.core.migration.PipelineConnectorScopeMigrationStatus;

import java.util.Set;

@OwnedBy(PL)
public class NGMorphiaRegistrars implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(ApiKey.class);
    set.add(Token.class);
    set.add(NGManagerUniqueIdParentIdMigrationStatus.class);
    set.add(NGManagerCDCEntitiesMigrationStatus.class);
    set.add(NgManagerTsdbUniqueIdParentIdMigrationStatus.class);
    set.add(RecomputeParentUniqueIdMigrationStatus.class);
    set.add(PipelineConnectorScopeMigrationStatus.class);
    set.add(AccountStatisticsTracker.class);
    set.add(CostSettings.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    // nothing to register
  }
}
