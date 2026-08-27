/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.utils.ConfigType;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface AppConfigRepository extends CrudRepository<AppConfigEntity, String>, AppConfigRepositoryCustom {
  List<AppConfigEntity> findAllByAccountIdentifierAndConfigType(String accountIdentifier, ConfigType configType);
  Optional<AppConfigEntity> findByAccountIdentifierAndConfigIdAndConfigType(
      String accountIdentifier, String configId, ConfigType configType);
  List<AppConfigEntity> findAllByAccountIdentifierAndEnabled(String accountIdentifier, Boolean enabled);

  List<AppConfigEntity> findAllByAccountIdentifierAndConfigTypeAndEnabled(
      String accountIdentifier, ConfigType configType, Boolean enabled);

  AppConfigEntity findByAccountIdentifierAndConfigId(String accountIdentifier, String configId);

  List<AppConfigEntity> findAll();
  void deleteByAccountIdentifierAndConfigIdAndConfigType(
      String accountIdentifier, String configId, ConfigType configType);
  List<AppConfigEntity> findAllByConfigIdIn(List<String> configIds);
  List<AppConfigEntity> findAllByConfigType(ConfigType configType);
  List<AppConfigEntity> findAllByConfigTypeAndConfigId(ConfigType configType, String configId);
}
