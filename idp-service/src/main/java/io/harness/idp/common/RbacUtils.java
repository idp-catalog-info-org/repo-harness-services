/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.common;

import static io.harness.accesscontrol.principals.PrincipalType.API_KEY;
import static io.harness.accesscontrol.principals.PrincipalType.SERVICE;
import static io.harness.accesscontrol.principals.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.security.SecurityContextBuilder.UNIQUE_ID;

import io.harness.accesscontrol.acl.api.Principal;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class RbacUtils {
  public boolean isPureServiceToServiceCall() {
    io.harness.security.dto.Principal principal = SecurityContextBuilder.getPrincipal();

    if (principal.getType() != io.harness.security.dto.PrincipalType.SERVICE) {
      return false;
    }

    io.harness.security.dto.Principal sourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();

    boolean isPureServiceCall =
        sourcePrincipal == null || sourcePrincipal.getType() == io.harness.security.dto.PrincipalType.SERVICE;

    return isPureServiceCall;
  }

  public Principal fromSecurityPrincipalType(io.harness.security.dto.PrincipalType principalType) {
    switch (principalType) {
          case SERVICE -> {
              if (SourcePrincipalContextBuilder.getSourcePrincipal() != null) {
                  return Principal.of(USER, SourcePrincipalContextBuilder.getSourcePrincipal().getName());
              }
              return Principal.of(SERVICE, SecurityContextBuilder.getPrincipal().getName());
          }
          case USER -> {
              return Principal.of(USER, SecurityContextBuilder.getPrincipal().getName());
          }
          case API_KEY -> {
              return Principal.of(API_KEY, SecurityContextBuilder.getPrincipal().getName());
          }
          case SERVICE_ACCOUNT -> {
              return Principal.of(SERVICE_ACCOUNT, SecurityContextBuilder.getPrincipal().getName(),
                  SecurityContextBuilder.getPrincipal().getJWTClaims().get(UNIQUE_ID));
          }
          default -> throw new InvalidRequestException("Unknown principal type found");
      }
  }

    public Principal fromSecurityPrincipalTypeWithoutModifying(io.harness.security.dto.PrincipalType principalType) {
        switch (principalType) {
            case SERVICE -> {
                return Principal.of(SERVICE, SecurityContextBuilder.getPrincipal().getName());
            }
            case USER -> {
                return Principal.of(USER, SecurityContextBuilder.getPrincipal().getName());
            }
            case API_KEY -> {
                return Principal.of(API_KEY, SecurityContextBuilder.getPrincipal().getName());
            }
            case SERVICE_ACCOUNT -> {
                return Principal.of(SERVICE_ACCOUNT, SecurityContextBuilder.getPrincipal().getName(),
                        SecurityContextBuilder.getPrincipal().getJWTClaims().get(UNIQUE_ID));
            }
            default -> throw new InvalidRequestException("Unknown principal type found");
        }
    }
}
