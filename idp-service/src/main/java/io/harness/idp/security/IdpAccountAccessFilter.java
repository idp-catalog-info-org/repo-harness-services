/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.security;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static javax.ws.rs.Priorities.AUTHORIZATION;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.WingsException;
import io.harness.request.AccountIdentifierExtractor;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.Singleton;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Provider
@Singleton
@Priority(AUTHORIZATION)
@OwnedBy(IDP)
public class IdpAccountAccessFilter implements ContainerRequestFilter {
  @Override
  public void filter(ContainerRequestContext requestContext) {
    String requestedAccount = AccountIdentifierExtractor.extract(requestContext);
    if (isEmpty(requestedAccount)) {
      return;
    }

    String principalAccount = getPrincipalAccountId(SecurityContextBuilder.getPrincipal());
    if (isEmpty(principalAccount)) {
      return;
    }

    if (!principalAccount.equals(requestedAccount)) {
      log.warn("principal account {} does not match requested account {}", principalAccount, requestedAccount);
      throw new NGAccessDeniedException("Access denied", WingsException.USER, null);
    }
  }

  private static String getPrincipalAccountId(Principal principal) {
    if (principal instanceof UserPrincipal userPrincipal) {
      return userPrincipal.getAccountId();
    }
    if (principal instanceof ServiceAccountPrincipal serviceAccountPrincipal) {
      return serviceAccountPrincipal.getAccountId();
    }
    return null;
  }
}
