/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.plan.PrincipalType.API_KEY;
import static io.harness.pms.contracts.plan.PrincipalType.SERVICE;
import static io.harness.pms.contracts.plan.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.pms.contracts.plan.PrincipalType.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.WingsException;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.PrincipalType;
import io.harness.rmg.helper.RmgSourcePrincipalRbacHelper;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.UserPrincipal;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;

@OwnedBy(PIPELINE)
public class PrincipalInfoHelper {
  @Inject PipelineServiceConfiguration configuration;
  @Inject RmgSourcePrincipalRbacHelper rmgSourcePrincipalRbacHelper;

  public ExecutionPrincipalInfo getPrincipalInfoFromSecurityContext() {
    Optional<ExecutionPrincipalInfo> rmgSourceExecutionPrincipal =
        rmgSourcePrincipalRbacHelper.getRmgSourceExecutionPrincipalInfo();
    if (rmgSourceExecutionPrincipal.isPresent()) {
      return rmgSourceExecutionPrincipal.get();
    }

    io.harness.security.dto.Principal securityContextPrincipal = SecurityContextBuilder.getPrincipal();
    io.harness.security.dto.Principal sourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    io.harness.security.dto.Principal principalInContext = securityContextPrincipal;

    if (shouldUseSourcePrincipal(securityContextPrincipal, sourcePrincipal)) {
      principalInContext = sourcePrincipal;
    }

    if (principalInContext == null || principalInContext.getName() == null || principalInContext.getType() == null) {
      throw new AccessDeniedException("Principal cannot be null", ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
    return ExecutionPrincipalInfo.newBuilder()
        .setPrincipal(principalInContext.getName())
        .setPrincipalType(Objects.requireNonNull(fromSecurityPrincipalType(principalInContext.getType())))
        .setShouldValidateRbac(true)
        .setPrincipalUniqueId(extractUniqueId(principalInContext))
        .build();
  }

  private String extractUniqueId(io.harness.security.dto.Principal principal) {
    if (principal instanceof ServiceAccountPrincipal) {
      String uniqueId = ((ServiceAccountPrincipal) principal).getUniqueId();
      return uniqueId != null ? uniqueId : "";
    }
    if (principal instanceof UserPrincipal) {
      String uniqueId = ((UserPrincipal) principal).getUniqueId();
      return uniqueId != null ? uniqueId : principal.getName();
    }
    return principal.getName() != null ? principal.getName() : "";
  }

  /**
   * Use SourcePrincipal when pipeline-service is in SecurityContext but the real executor (USER/SA) is in
   * SourcePrincipal. This split exists only in the trigger executor flow
   * ({@code TriggerExecutorResolver#setExecutorContext}).
   */
  @VisibleForTesting
  boolean shouldUseSourcePrincipal(
      io.harness.security.dto.Principal securityContextPrincipal, io.harness.security.dto.Principal sourcePrincipal) {
    if (sourcePrincipal == null || sourcePrincipal.getName() == null || sourcePrincipal.getType() == null) {
      return false;
    }
    if (securityContextPrincipal == null || securityContextPrincipal.getType() == null
        || securityContextPrincipal.getType() != io.harness.security.dto.PrincipalType.SERVICE) {
      return false;
    }
    return sourcePrincipal.getType() == io.harness.security.dto.PrincipalType.USER
        || sourcePrincipal.getType() == io.harness.security.dto.PrincipalType.SERVICE_ACCOUNT;
  }

  @VisibleForTesting
  PrincipalType fromSecurityPrincipalType(io.harness.security.dto.PrincipalType principalType) {
    switch (principalType) {
      case SERVICE:
        return SERVICE;
      case API_KEY:
        return API_KEY;
      case USER:
        return USER;
      case SERVICE_ACCOUNT:
        return SERVICE_ACCOUNT;
      default:
        return null;
    }
  }
}
