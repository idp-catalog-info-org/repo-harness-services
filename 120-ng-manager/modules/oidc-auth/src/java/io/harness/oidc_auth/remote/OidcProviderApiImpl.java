/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc_auth.remote;

import static io.harness.ng.accesscontrol.PlatformPermissions.DELETE_AUTHSETTING_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.EDIT_AUTHSETTING_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_AUTHSETTING_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.AUTHSETTING;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptedSecretValue;
import io.harness.beans.ScopeInfo;
import io.harness.eraro.ResponseMessage;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.oidc_auth.service.OidcProviderService;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.OidcProviderApi;
import io.harness.spec.server.ng.v1.model.OidcProviderDTO;
import io.harness.utils.IdentifierRefHelper;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PL)
@Timed
@ResponseMetered
@NextGenManagerAuth
public class OidcProviderApiImpl implements OidcProviderApi {
  OidcProviderService oidcProviderService;
  NGEncryptedDataService ngEncryptedDataService;
  AccessControlClient accessControlClient;
  @Override
  public Response deleteOidcProvider(String oidcProviderId, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(harnessAccount, null, null), Resource.of(AUTHSETTING, null), DELETE_AUTHSETTING_PERMISSION);
    try {
      boolean response = oidcProviderService.deleteOidcProvider(harnessAccount, oidcProviderId);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (Exception ex) {
      log.error("Failed to delete OIDC provider- {} for account {}", oidcProviderId, harnessAccount, ex);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder()
                      .message(String.format("Failed to delete the OIDC provider. Reason- %s", ex.getMessage()))
                      .build())
          .build();
    }
  }

  @Override
  public Response listOidcProvider(String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(harnessAccount, null, null), Resource.of(AUTHSETTING, null), VIEW_AUTHSETTING_PERMISSION);
    try {
      List<OidcProviderDTO> response = oidcProviderService.getOidcProvidersForAccount(harnessAccount);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (Exception ex) {
      log.error("Failed to fetch OIDC Providers for account {}", harnessAccount, ex);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder()
                      .message(String.format("Failed to fetch OIDC providers. Reason- %s", ex.getMessage()))
                      .build())
          .build();
    }
  }

  @Override
  public Response getOidcProvider(String oidcProviderId, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(harnessAccount, null, null), Resource.of(AUTHSETTING, null), VIEW_AUTHSETTING_PERMISSION);
    try {
      OidcProviderDTO response = oidcProviderService.getOidcProvider(harnessAccount, oidcProviderId);

      if (response == null) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ResponseMessage.builder()
                        .message(String.format("OIDC provider with identifier %s does not exist for account %s",
                            oidcProviderId, harnessAccount))
                        .build())
            .build();
      }

      return Response.status(Response.Status.OK).entity(response).build();
    } catch (Exception ex) {
      log.error("Failed to fetch OIDC Providers for account {}", harnessAccount, ex);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder()
                      .message(String.format(
                          "Failed to fetch the OIDC provider- %s. Reason- %s", oidcProviderId, ex.getMessage()))
                      .build())
          .build();
    }
  }

  @Override
  @InternalApi
  public Response getOidcProviderInternal(String oidcProviderId, String harnessAccount) {
    try {
      OidcProviderDTO response = oidcProviderService.getOidcProvider(harnessAccount, oidcProviderId);

      if (response == null) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ResponseMessage.builder()
                        .message(String.format("OIDC provider with identifier %s does not exist for account %s",
                            oidcProviderId, harnessAccount))
                        .build())
            .build();
      }

      String secretIdentifier = IdentifierRefHelper.getIdentifier(response.getClientConfig().getSecretRef());
      DecryptedSecretValue secretValue = ngEncryptedDataService.decryptSecret(
          ScopeInfo.builder().accountIdentifier(harnessAccount).uniqueId(harnessAccount).build(), secretIdentifier);
      response.getClientConfig().setSecretRef(secretValue.getDecryptedValue());

      return Response.status(Response.Status.OK).entity(response).build();
    } catch (Exception ex) {
      log.error("Failed to fetch decrypted OIDC Providers for account {}", harnessAccount, ex);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder()
                      .message(String.format(
                          "Failed to fetch the OIDC provider- %s. Reason- %s", oidcProviderId, ex.getMessage()))
                      .build())
          .build();
    }
  }

  @Override
  public Response createOidcProvider(@Valid OidcProviderDTO oidcProviderDTO, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(harnessAccount, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    try {
      OidcProviderDTO response = oidcProviderService.createOidcProvider(harnessAccount, oidcProviderDTO);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (Exception ex) {
      log.error("Failed to create OIDC Providers for account {}", harnessAccount, ex);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder()
                      .message(String.format("Failed to create OIDC provider. Reason- %s", ex.getMessage()))
                      .build())
          .build();
    }
  }

  @Override
  public Response updateOidcProvider(String oidcProviderId, OidcProviderDTO oidcProviderDTO, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(harnessAccount, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    try {
      OidcProviderDTO response =
          oidcProviderService.updateOidcProvider(harnessAccount, oidcProviderId, oidcProviderDTO);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (Exception ex) {
      log.error("Failed to update OIDC Providers for account {}", harnessAccount, ex);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder()
                      .message(String.format("Failed to update OIDC provider. Reason- %s", ex.getMessage()))
                      .build())
          .build();
    }
  }
}
