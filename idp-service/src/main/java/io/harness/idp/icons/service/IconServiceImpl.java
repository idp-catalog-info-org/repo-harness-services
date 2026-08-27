/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.icons.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.common.CloudStorageUtil;
import io.harness.idp.common.IconUploadType;
import io.harness.idp.common.IconUtils;
import io.harness.idp.homepage.config.HomePageCardIconConfig;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Slf4j
public class IconServiceImpl implements IconService {
  @Inject CloudStorageUtil cloudStorageUtil;
  @Inject @Named("homePageCardIconConfig") HomePageCardIconConfig homePageCardIconConfig;

  @Inject @Named("env") String env;

  public List<String> getAllIcons(String accountIdentifier) {
    List<String> urls = new ArrayList<>();
    for (IconUploadType iconUploadType : IconUploadType.values()) {
      urls.addAll(cloudStorageUtil.fetchImageUrls(
          homePageCardIconConfig.getBucketName(), IconUtils.getIconPath(accountIdentifier, iconUploadType, env)));
    }
    return urls;
  }
}
