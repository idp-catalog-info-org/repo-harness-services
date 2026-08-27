/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.resource;

import static io.harness.idp.common.RbacConstants.IDP_PLUGIN;
import static io.harness.idp.common.RbacConstants.IDP_PLUGIN_DELETE;
import static io.harness.idp.common.RbacConstants.IDP_PLUGIN_EDIT;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.configmanager.service.PluginInfoService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.model.CustomPluginDetailedInfo;
import io.harness.spec.server.idp.v1.model.CustomPluginInfoResponse;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.cloud.storage.StorageException;
import com.google.inject.Inject;
import java.io.InputStream;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@Slf4j
@Timed
@ResponseMetered
public class PluginFileUploadApiImpl implements PluginFileUploadApi {
  private PluginInfoService pluginInfoService;

  @Override
  @NGAccessControlCheck(resourceType = IDP_PLUGIN, permission = IDP_PLUGIN_EDIT)
  public Response uploadCustomPluginFile(String pluginId, String fileType, InputStream fileInputStream,
      FormDataContentDisposition fileDetail, @AccountIdentifier String harnessAccount) {
    try {
      CustomPluginDetailedInfo info =
          pluginInfoService.uploadFile(pluginId, fileType, fileInputStream, fileDetail, harnessAccount);
      CustomPluginInfoResponse response = new CustomPluginInfoResponse();
      response.setInfo(info);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (NotFoundException e) {
      log.error("Could not find custom plugin with id {} in account {}", pluginId, harnessAccount, e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (UnsupportedOperationException e) {
      log.error("Could not upload file for plugin {}", pluginId, e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (StorageException e) {
      log.error("Could not upload file for plugin {}", pluginId, e);
      return Response.status(e.getCode()).entity(ResponseMessage.builder().message(e.getMessage()).build()).build();
    } catch (Exception e) {
      log.error("Failed to upload upload file for plugin {}", pluginId, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_PLUGIN, permission = IDP_PLUGIN_DELETE)
  public Response deletePluginFile(
      String pluginId, @AccountIdentifier String harnessAccount, String fileType, String fileName) {
    try {
      pluginInfoService.deleteFile(pluginId, fileType, fileName, harnessAccount);
      return Response.status(Response.Status.NO_CONTENT).build();
    } catch (NotFoundException e) {
      log.error("Could not find custom plugin with id {} in account {}", pluginId, harnessAccount, e);
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (UnsupportedOperationException e) {
      log.error("Could not delete file for plugin {}", pluginId, e);
      return Response.status(Response.Status.BAD_REQUEST).build();
    } catch (StorageException e) {
      log.error("Could not delete file for plugin {}", pluginId, e);
      return Response.status(e.getCode()).build();
    } catch (Exception e) {
      log.error("Failed to delete file for plugin {}", pluginId, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }
}
