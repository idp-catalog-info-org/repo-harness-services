/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.account.overrides;

import static io.harness.configuration.DeployVariant.DEPLOY_VERSION;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.configuration.DeployVariant;
import io.harness.licensing.Edition;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class EditionBasedAccountConfigStrategyFactory {
  @Inject FreeAccountConfigStrategy freeAccountConfigStrategy;
  @Inject EnterpriseAccountConfigStrategy enterpriseAccountConfigStrategy;
  @Inject TeamAccountConfigStrategy teamAccountConfigStrategy;
  @Inject CommunityAccountConfigStrategy communityAccountConfigStrategy;
  @Inject DevopsAccountConfigStrategy devopsAccountConfigStrategy;
  @Inject EssentialsAccountConfigStrategy essentialsAccountConfigStrategy;

  public EditionBasedAccountConfigStrategy getStrategy(Edition edition) {
    switch (edition) {
      case FREE:
        return freeAccountConfigStrategy;
      case TEAM, STARTUP:
        return teamAccountConfigStrategy;
      case COMMUNITY: // This is valid for SMP env only
        return communityAccountConfigStrategy;
      case ENTERPRISE:
        return enterpriseAccountConfigStrategy;
      case DEVOPS_ESSENTIALS:
        return devopsAccountConfigStrategy;
      case ESSENTIALS:
        return essentialsAccountConfigStrategy;
      default:
        // Adding this condition because there might be cases where edition is null for smp.
        if (DeployVariant.isCommunity(System.getenv().get(DEPLOY_VERSION))) {
          return communityAccountConfigStrategy;
        }
        log.warn("Unidentified edition" + edition);
        return freeAccountConfigStrategy;
    }
  }
}
