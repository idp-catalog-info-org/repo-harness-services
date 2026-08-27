/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.ngtriggers.Constants.UNIQUE_ID;

import io.harness.beans.FeatureName;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.security.PrincipalHelper;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class TriggeredByHelper {
  public static final String RMG_SERVICE = "rmservice";
  public static final String RMG_SERVICE_IDENTIFIER = "ReleaseOrchestration";
  public static final String SOURCE_SERVICE = "sourceService";
  @Inject private CurrentUserHelper currentUserHelper;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;

  public TriggeredBy getFromSecurityContext() {
    Principal principal = currentUserHelper.getPrincipalFromSecurityContext();
    Principal authPrincipal = SecurityContextBuilder.getPrincipal();

    /*
     * If the authenticated principal is the RMG service, build the TriggeredBy
     * using the source principal's user details while setting the identifier to
     * ReleaseOrchestration.
     */
    if (authPrincipal instanceof ServicePrincipal authServicePrincipal
        && RMG_SERVICE.equals(authServicePrincipal.getName())
        && pmsFeatureFlagService.isEnabled(
            PrincipalHelper.getAccountId(principal), FeatureName.RMG_ENFORCE_SOURCE_PRINCIPAL_RBAC)) {
      return TriggeredBy.newBuilder()
          .setUuid(emptyIfNull(PrincipalHelper.getUuid(principal)))
          .setIdentifier(RMG_SERVICE_IDENTIFIER)
          .putExtraInfo("email", emptyIfNull(PrincipalHelper.getEmail(principal)))
          .putExtraInfo(UNIQUE_ID, emptyIfNull(PrincipalHelper.getUniqueId(principal)))
          .build();
    }

    /*
     * If the triggering principal itself is the RMG service, build a service-level
     * TriggeredBy without user-specific details.
     */
    if (principal instanceof ServicePrincipal servicePrincipal && RMG_SERVICE.equals(servicePrincipal.getName())) {
      return TriggeredBy.newBuilder()
          .setUuid(emptyIfNull(PrincipalHelper.getUuid(principal)))
          .setIdentifier(RMG_SERVICE_IDENTIFIER)
          .build();
    }

    TriggeredBy.Builder triggeredBy = TriggeredBy.newBuilder()
                                          .setUuid(emptyIfNull(PrincipalHelper.getUuid(principal)))
                                          .setIdentifier(emptyIfNull(PrincipalHelper.getUsername(principal)))
                                          .putExtraInfo("email", emptyIfNull(PrincipalHelper.getEmail(principal)))
                                          .putExtraInfo(UNIQUE_ID, emptyIfNull(PrincipalHelper.getUniqueId(principal)));
    if (authPrincipal instanceof ServicePrincipal authServicePrincipal
        && RMG_SERVICE.equals(authServicePrincipal.getName())) {
      triggeredBy.putExtraInfo(SOURCE_SERVICE, RMG_SERVICE);
    }
    if (!(principal instanceof UserPrincipal)) {
      return triggeredBy.build();
    }
    UserPrincipal userPrincipal = (UserPrincipal) principal;
    if (userPrincipal.getImpersonatingPrincipal() != null) {
      String impersonateEmail = userPrincipal.getImpersonatingPrincipal().getEmail();
      String impersonateUsername = userPrincipal.getImpersonatingPrincipal().getUsername();
      if (impersonateEmail != null) {
        triggeredBy.setImpersonateEmail(impersonateEmail);
      }
      if (impersonateUsername != null) {
        triggeredBy.setImpersonateUsername(impersonateUsername);
      }
    }
    return triggeredBy.build();
  }
}
