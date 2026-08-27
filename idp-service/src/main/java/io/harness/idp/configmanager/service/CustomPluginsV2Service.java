/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.entities.CustomPluginV2Entity;
import io.harness.spec.server.idp.v1.model.CustomPluginV2CreateRequest;
import io.harness.spec.server.idp.v1.model.CustomPluginV2Response;
import io.harness.spec.server.idp.v1.model.CustomPluginV2UpdateRequest;

import java.io.InputStream;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.springframework.data.domain.Page;

@OwnedBy(HarnessTeam.IDP)
public interface CustomPluginsV2Service {
  CustomPluginV2Response createCustomPlugin(String accountIdentifier, CustomPluginV2CreateRequest request);

  CustomPluginV2Response getCustomPlugin(String accountIdentifier, String pluginId);

  Page<CustomPluginV2Entity> getAllCustomPlugins(
      String accountIdentifier, Integer page, Integer limit, String sort, String searchTerm);

  CustomPluginV2Response updateCustomPlugin(
      String accountIdentifier, String pluginId, CustomPluginV2UpdateRequest request);

  void deleteCustomPlugin(String accountIdentifier, String pluginId);

  CustomPluginV2Response uploadHtmlFile(
      String accountIdentifier, String pluginId, InputStream fileInputStream, FormDataContentDisposition fileDetail);

  void deleteHtmlFile(String accountIdentifier, String pluginId);

  String getFileContent(String accountIdentifier, String pluginId);
}
