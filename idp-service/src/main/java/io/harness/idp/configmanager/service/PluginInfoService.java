/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.service;

import io.harness.idp.configmanager.entities.PluginRequestEntity;
import io.harness.spec.server.idp.v1.model.CustomPluginDetailedInfo;
import io.harness.spec.server.idp.v1.model.PluginDetailedInfo;
import io.harness.spec.server.idp.v1.model.PluginInfo;
import io.harness.spec.server.idp.v1.model.PluginRequestStatus;
import io.harness.spec.server.idp.v1.model.RequestPlugin;
import io.harness.spec.server.idp.v1.model.RequestPluginByIdAndStatus;

import java.io.InputStream;
import java.util.List;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.springframework.data.domain.Page;

public interface PluginInfoService {
  List<PluginInfo> getAllPluginsInfo(String harnessAccount, Boolean isDeleted);
  PluginDetailedInfo getPluginDetailedInfo(String identifier, String harnessAccount, boolean meta);
  void saveAllPluginInfo();
  void deleteAllPluginInfo();
  RequestPlugin savePluginRequest(String harnessAccount, RequestPlugin pluginRequest);
  void savePluginRequestV2(String harnessAccount, RequestPluginByIdAndStatus requestPluginByIdAndStatus);
  Page<PluginRequestEntity> getPluginRequests(String harnessAccount, int page, int limit);
  PluginRequestEntity updatePluginRequest(
      String accountIdentifier, String identifier, PluginRequestStatus pluginStatus);
  void updatePluginsMetadataOnGcs(String accountIdentifier);

  void saveDefaultPluginInfo(String fileName, List<String> accountIdentifiers) throws Exception;

  CustomPluginDetailedInfo generateIdentifierAndSaveCustomPluginInfo(
      String accountIdentifier, CustomPluginDetailedInfo customCreatePluginDetailedInfo);

  CustomPluginDetailedInfo updatePluginInfo(String pluginId, CustomPluginDetailedInfo info, String harnessAccount);

  CustomPluginDetailedInfo uploadFile(String pluginId, String info, InputStream fileInputStream,
      FormDataContentDisposition fileDetail, String harnessAccount);

  CustomPluginDetailedInfo deleteFile(String pluginId, String fileType, String fileName, String harnessAccount);

  void deletePluginInfo(String pluginId, String harnessAccount, String pluginName);

  void syncMarketPlacePlugins();
}
