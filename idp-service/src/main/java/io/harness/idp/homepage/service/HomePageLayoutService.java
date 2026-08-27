/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.homepage.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.IconUploadType;
import io.harness.spec.server.idp.v1.model.HomePageLayoutRequest;
import io.harness.spec.server.idp.v1.model.HomePageLayoutResponse;
import io.harness.spec.server.idp.v1.model.HomePageLayoutYamlResponse;

import java.io.InputStream;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

@OwnedBy(HarnessTeam.IDP)
public interface HomePageLayoutService {
  HomePageLayoutResponse getHomePageLayout(String accountIdentifier);
  HomePageLayoutResponse saveHomePageLayout(HomePageLayoutRequest homePageLayoutRequest, String accountIdentifier);
  HomePageLayoutYamlResponse getHomePageLayoutYaml(String accountIdentifier);
  String uploadIcon(IconUploadType type, String identifier, String fileType, InputStream fileInputStream,
      FormDataContentDisposition fileDetail, String harnessAccount);
  void deleteCardIcon(String accountId, String cardIdentifier);

  void deleteHeaderQuickLinksIcon(String accountIdentifier, String quickLinksIdentifier);
  void deleteCustomCardQuickLinksIcon(String accountIdentifier, String cardIdentifier, String quickLinksIdentifier);

  void deleteHomePageLayoutIcon(String accountIdentifier, String iconUrl);
}
