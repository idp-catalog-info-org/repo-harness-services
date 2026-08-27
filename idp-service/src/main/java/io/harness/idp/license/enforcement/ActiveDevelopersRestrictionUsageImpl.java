/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.enforcement;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.idp.license.usage.service.IDPModuleLicenseUsage;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class ActiveDevelopersRestrictionUsageImpl
    implements RestrictionUsageInterface<StaticLimitRestrictionMetadataDTO> {
  @Inject IDPModuleLicenseUsage idpModuleLicenseUsage;

  @Override
  public long getCurrentValue(String accountIdentifier, StaticLimitRestrictionMetadataDTO restrictionMetadataDTO) {
    return idpModuleLicenseUsage.getActiveDevelopers(accountIdentifier);
  }
}
