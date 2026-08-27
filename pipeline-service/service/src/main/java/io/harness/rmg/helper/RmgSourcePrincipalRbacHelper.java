/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rmg.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.plan.PrincipalType.USER;

import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the initiating user for Release Orchestration (RMG) pipeline execute calls that authenticate as the
 * {@code rmservice} SERVICE principal but carry the real user in {@code X-Source-Principal}.
 */
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class RmgSourcePrincipalRbacHelper {
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;

  /**
   * Returns the source USER principal when the request is an RMG service call with a validated user source principal
   * and {@link FeatureName#RMG_ENFORCE_SOURCE_PRINCIPAL_RBAC} is enabled for the account.
   */
  public Optional<UserPrincipal> getRmgSourceUserPrincipal() {
    io.harness.security.dto.Principal authPrincipal = SecurityContextBuilder.getPrincipal();
    io.harness.security.dto.Principal sourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();

    if (!(authPrincipal instanceof ServicePrincipal servicePrincipal)) {
      return Optional.empty();
    }
    if (!RmgConstants.RMG_SERVICE.equals(servicePrincipal.getName())) {
      return Optional.empty();
    }
    if (!(sourcePrincipal instanceof UserPrincipal userPrincipal)) {
      return Optional.empty();
    }
    if (userPrincipal.getAccountId() == null) {
      log.warn("RMG source principal user is missing accountId; skipping user-scoped RBAC");
      return Optional.empty();
    }
    if (!pmsFeatureFlagService.isEnabled(userPrincipal.getAccountId(), FeatureName.RMG_ENFORCE_SOURCE_PRINCIPAL_RBAC)) {
      return Optional.empty();
    }
    return Optional.of(userPrincipal);
  }

  public Principal toAccessControlPrincipal(UserPrincipal userPrincipal) {
    return Principal.of(PrincipalType.USER, userPrincipal.getName(),
        userPrincipal.getUniqueId() != null ? userPrincipal.getUniqueId() : userPrincipal.getName());
  }

  public Optional<ExecutionPrincipalInfo> getRmgSourceExecutionPrincipalInfo() {
    return getRmgSourceUserPrincipal().map(user
        -> ExecutionPrincipalInfo.newBuilder()
               .setPrincipal(user.getName())
               .setPrincipalType(USER)
               .setShouldValidateRbac(true)
               .setPrincipalUniqueId(user.getUniqueId() != null ? user.getUniqueId() : user.getName())
               .build());
  }
}
