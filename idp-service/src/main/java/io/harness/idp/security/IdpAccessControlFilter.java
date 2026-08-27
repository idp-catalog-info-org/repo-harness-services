/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.security;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import static javax.ws.rs.Priorities.AUTHORIZATION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.moduleaccess.ModuleAccessControlFilter;

import com.google.inject.Singleton;
import javax.annotation.Priority;
import javax.ws.rs.ext.Provider;

/**
 * IDP-specific module access control filter.
 *
 * Enforces that the authenticated principal has the "idp_module_access" permission on the "IDP"
 * resource type. Only active when the PL_NAMED_USERS feature flag is enabled for the account.
 * reference PR:
 * https://harness0.harness.io/ng/account/l7B_kbSEQD2wjrM7PShm5w/module/code/orgs/PROD/projects/Harness_Commons/repos/harness-core/pulls/113054/changes
 */
@Provider
@Singleton
@Priority(AUTHORIZATION)
@OwnedBy(IDP)
public class IdpAccessControlFilter extends ModuleAccessControlFilter {
  @Override
  protected String getPermission() {
    return "idp_module_access";
  }

  @Override
  protected String getResourceType() {
    return "MODULE";
  }

  @Override
  protected FeatureName getFeatureFlag() {
    return FeatureName.PL_NAMED_USERS;
  }
}
