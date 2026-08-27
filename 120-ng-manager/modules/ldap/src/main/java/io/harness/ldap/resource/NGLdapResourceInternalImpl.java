/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ldap.resource;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ldap.dto.NGLdapSettingsWithEncryptedDataDetailsDTO;
import io.harness.ldap.mapper.NgLdapSettingsMapper;
import io.harness.ldap.service.NGLdapSettingsService;
import io.harness.rest.RestResponse;
import io.harness.spec.server.ng.v1.model.LdapSettingsDTO;
import io.harness.sso.NGLdapSettingsWithEncryptedDataDetails;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("/ldap-settings-internal")
@Path("/ldap-settings-internal")
@Produces("application/json")
@Hidden
@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class NGLdapResourceInternalImpl implements NGLdapResourceInternal {
  private NGLdapSettingsService ldapSettingsService;
  private NgLdapSettingsMapper ngLdapSettingsMapper;

  @Override
  public RestResponse<NGLdapSettingsWithEncryptedDataDetails> getNGLdapSettingsWithEncryptedDataDetail(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier) {
    NGLdapSettingsWithEncryptedDataDetailsDTO ngLdapSettingsWithEncryptedDataDetailsDTO =
        ldapSettingsService.getLdapSettingsWithEncryptedDataDetails(accountIdentifier);
    LdapSettingsDTO ldapSettingsDTO =
        ngLdapSettingsMapper.toLdapSettingsDTO(ngLdapSettingsWithEncryptedDataDetailsDTO.getNgLdapSettings());
    return new RestResponse<>(
        NGLdapSettingsWithEncryptedDataDetails.builder()
            .ldapSettings(ldapSettingsDTO)
            .encryptedDataDetail(ngLdapSettingsWithEncryptedDataDetailsDTO.getEncryptedDataDetail())
            .build());
  }
}
