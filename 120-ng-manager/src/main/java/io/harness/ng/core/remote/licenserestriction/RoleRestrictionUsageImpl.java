/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote.licenserestriction;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class RoleRestrictionUsageImpl implements RestrictionUsageInterface<StaticLimitRestrictionMetadataDTO> {
  private final AccessControlAdminClient accessControlAdminClient;

  @Inject
  public RoleRestrictionUsageImpl(@Named("PRIVILEGED") AccessControlAdminClient accessControlAdminClient) {
    this.accessControlAdminClient = accessControlAdminClient;
  }

  @Override
  public long getCurrentValue(String accountIdentifier, StaticLimitRestrictionMetadataDTO restrictionMetadataDTO) {
    return NGRestUtils.getGeneralResponse(accessControlAdminClient.getRolesCount(accountIdentifier));
  }
}
