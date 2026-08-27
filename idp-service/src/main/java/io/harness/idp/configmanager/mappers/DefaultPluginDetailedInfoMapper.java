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
import io.harness.exception.InvalidRequestException;
import io.harness.idp.configmanager.entities.DefaultPluginInfoEntity;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.DefaultPluginDetailedInfo;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;

import java.util.List;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class DefaultPluginDetailedInfoMapper
    implements PluginDetailedInfoMapper<DefaultPluginDetailedInfo, DefaultPluginInfoEntity> {
  @Override
  public DefaultPluginDetailedInfo toDto(DefaultPluginInfoEntity entity, AppConfig appConfig,
      List<BackstageEnvSecretVariable> secrets, List<ProxyHostDetail> hostDetails) {
    DefaultPluginDetailedInfo dto = new DefaultPluginDetailedInfo();
    setCommonFieldsDto(entity, dto, appConfig, secrets, hostDetails);
    return dto;
  }

  @Override
  public DefaultPluginInfoEntity fromDto(DefaultPluginDetailedInfo dto, String accountIdentifier) {
    throw new InvalidRequestException("Saving/Updating default plugins are not allowed.");
  }
}
