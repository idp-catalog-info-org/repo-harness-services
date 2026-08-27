/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.resource;

import static io.harness.idp.common.RbacConstants.IDP_PLUGIN;
import static io.harness.idp.common.RbacConstants.IDP_PLUGIN_DELETE;
import static io.harness.idp.common.RbacConstants.IDP_PLUGIN_EDIT;
import static io.harness.idp.common.RbacConstants.IDP_PLUGIN_VIEW;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.configmanager.service.CustomPluginsV2Service;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.model.CustomPluginV2Response;

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
@OwnedBy(HarnessTeam.IDP)
@Slf4j
@Timed
@ResponseMetered
public class CustomPluginsV2FileUploadApiImpl implements CustomPluginsV2FileUploadApi {
  private CustomPluginsV2Service customPluginV2Service;

  @Override
  @NGAccessControlCheck(resourceType = IDP_PLUGIN, permission = IDP_PLUGIN_EDIT)
  public Response uploadHtmlFile(String pluginId, InputStream fileInputStream, FormDataContentDisposition fileDetail,
      @AccountIdentifier String harnessAccount) {
    try {
      CustomPluginV2Response response =
          customPluginV2Service.uploadHtmlFile(harnessAccount, pluginId, fileInputStream, fileDetail);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (NotFoundException e) {
      log.error("Custom plugin not found: {} in account {}", pluginId, harnessAccount, e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (UnsupportedOperationException e) {
      log.error("Unsupported file format for plugin {}", pluginId, e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (StorageException e) {
      log.error("GCS storage error uploading file for plugin {}", pluginId, e);
      return Response.status(e.getCode()).entity(ResponseMessage.builder().message(e.getMessage()).build()).build();
    } catch (Exception e) {
      log.error("Failed to upload HTML file for plugin {}", pluginId, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_PLUGIN, permission = IDP_PLUGIN_DELETE)
  public Response deleteHtmlFile(String pluginId, @AccountIdentifier String harnessAccount) {
    try {
      customPluginV2Service.deleteHtmlFile(harnessAccount, pluginId);
      return Response.status(Response.Status.NO_CONTENT).build();
    } catch (NotFoundException e) {
      log.error("Custom plugin or file not found: {} in account {}", pluginId, harnessAccount, e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (StorageException e) {
      log.error("GCS storage error deleting file for plugin {}", pluginId, e);
      return Response.status(e.getCode()).entity(ResponseMessage.builder().message(e.getMessage()).build()).build();
    } catch (Exception e) {
      log.error("Failed to delete HTML file for plugin {}", pluginId, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_PLUGIN, permission = IDP_PLUGIN_VIEW)
  public Response previewFile(String pluginId, @AccountIdentifier String harnessAccount) {
    try {
      String content = customPluginV2Service.getFileContent(harnessAccount, pluginId);
      return Response.status(Response.Status.OK).entity(content).type("text/html").build();
    } catch (NotFoundException e) {
      log.error("Custom plugin or file not found: {} in account {}", pluginId, harnessAccount, e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (Exception e) {
      log.error("Failed to preview file for plugin {}", pluginId, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
