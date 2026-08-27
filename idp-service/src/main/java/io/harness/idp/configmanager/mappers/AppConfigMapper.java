/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.mappers;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;

import java.util.List;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class AppConfigMapper {
  public AppConfig toDTO(AppConfigEntity appConfigEntity) {
    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(appConfigEntity.getConfigId());
    appConfig.setConfigName(appConfigEntity.getConfigName());
    appConfig.setConfigs(appConfigEntity.getConfigs());
    appConfig.setEnabledDisabledAt(appConfigEntity.getEnabledDisabledAt());
    appConfig.setEnabled(appConfigEntity.getEnabled());
    appConfig.setCreated(appConfigEntity.getCreatedAt());
    appConfig.setUpdated(appConfigEntity.getLastModifiedAt());
    return appConfig;
  }

  public AppConfig toDTO(AppConfigEntity appConfigEntity, List<ProxyHostDetail> proxyHostDetailList) {
    AppConfig appConfig = toDTO(appConfigEntity);
    appConfig.setProxy(proxyHostDetailList);
    return appConfig;
  }

  public AppConfigEntity fromDTO(AppConfig appConfig, String accountIdentifier) {
    return AppConfigEntity.builder()
        .accountIdentifier(accountIdentifier)
        .configId(appConfig.getConfigId())
        .configs(appConfig.getConfigs())
        .configName(appConfig.getConfigName())
        .build();
  }
}
