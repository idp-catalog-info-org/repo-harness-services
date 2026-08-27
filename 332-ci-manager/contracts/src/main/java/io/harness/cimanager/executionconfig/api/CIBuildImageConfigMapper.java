/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ci.pipeline.executions.beans;

import io.harness.beans.FeatureName;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.beans.entities.CIBuildImageVmConfig;
import io.harness.ci.ff.CIFeatureFlagService;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class CIBuildImageConfigMapper {
  public CIBuildImageVmConfig toEntity(BuildImageConfigDTO dto) {
    if (dto == null) {
      return null;
    }
    if (dto.getData() == null) {
      throw new IllegalArgumentException("Config is required and cannot be null");
    }
    return CIBuildImageVmConfig.builder().accountId(dto.getAccountId()).data(mapImageOSToEntity(dto.getData())).build();
  }

  private CIBuildImageVmConfig.OS mapImageOSToEntity(BuildImageConfigDTO.ImageOS imageOS) {
    if (imageOS == null) {
      return null;
    }

    return CIBuildImageVmConfig.OS.builder()
        .linux_amd64(mapEnvironmentTypeToEntity(imageOS.getLinux_amd64()))
        .linux_arm64(mapEnvironmentTypeToEntity(imageOS.getLinux_arm64()))
        .windows_amd64(mapEnvironmentTypeToEntity(imageOS.getWindows_amd64()))
        .mac_arm64(mapEnvironmentTypeToEntity(imageOS.getMac_arm64()))
        .build();
  }

  private CIBuildImageVmConfig.Environment mapEnvironmentTypeToEntity(BuildImageConfigDTO.EnvironmentType envType) {
    if (envType == null) {
      return null;
    }

    return CIBuildImageVmConfig.Environment.builder()
        .primary(mapImageDetailsListToEntity(envType.getPrimary()))
        .beta(mapImageDetailsListToEntity(envType.getBeta()))
        .build();
  }

  private List<CIBuildImageVmConfig.ImageDetailsConfig> mapImageDetailsListToEntity(
      List<BuildImageConfigDTO.ImageDetailsConfig> dtoList) {
    if (dtoList == null) {
      return null;
    }

    return dtoList.stream().map(dto -> mapImageDetailsDTOtoEntity(dto)).collect(Collectors.toList());
  }

  private CIBuildImageVmConfig.ImageDetailsConfig mapImageDetailsDTOtoEntity(
      BuildImageConfigDTO.ImageDetailsConfig dto) {
    if (dto == null) {
      return null;
    }
    return CIBuildImageVmConfig.ImageDetailsConfig.builder().version(dto.getVersion()).image(dto.getImage()).build();
  }

  public BuildImageConfigDTO toDTO(CIBuildImageVmConfig entity) {
    if (entity == null) {
      return null;
    }

    return BuildImageConfigDTO.builder()
        .accountId(entity.getAccountId())
        .data(mapOSEntityToDTO(entity.getData()))
        .build();
  }

  public BuildImageConfigDTO toDTO(
      CIBuildImageVmConfig entity, CIFeatureFlagService ciFeatureFlagService, String accountId) {
    if (entity == null) {
      return null;
    }

    return BuildImageConfigDTO.builder()
        .accountId(entity.getAccountId())
        .data(mapOSEntityToDTO(entity.getData(), ciFeatureFlagService, accountId))
        .build();
  }

  private BuildImageConfigDTO.ImageOS mapOSEntityToDTO(CIBuildImageVmConfig.OS os) {
    if (os == null) {
      return null;
    }

    return BuildImageConfigDTO.ImageOS.builder()
        .linux_amd64(mapEnvironmentEntityToDTO(os.getLinux_amd64()))
        .linux_arm64(mapEnvironmentEntityToDTO(os.getLinux_arm64()))
        .windows_amd64(mapEnvironmentEntityToDTO(os.getWindows_amd64()))
        .mac_arm64(mapEnvironmentEntityToDTO(os.getMac_arm64()))
        .build();
  }

  private BuildImageConfigDTO.ImageOS mapOSEntityToDTO(
      CIBuildImageVmConfig.OS os, CIFeatureFlagService ciFeatureFlagService, String accountId) {
    if (os == null) {
      return null;
    }

    return BuildImageConfigDTO.ImageOS.builder()
        .linux_amd64(mapEnvironmentEntityToDTO(os.getLinux_amd64(), OSType.Linux, ciFeatureFlagService, accountId))
        .linux_arm64(mapEnvironmentEntityToDTO(os.getLinux_arm64(), OSType.Linux, ciFeatureFlagService, accountId))
        .windows_amd64(
            mapEnvironmentEntityToDTO(os.getWindows_amd64(), OSType.Windows, ciFeatureFlagService, accountId))
        .mac_arm64(mapEnvironmentEntityToDTO(os.getMac_arm64(), OSType.MacOS, ciFeatureFlagService, accountId))
        .build();
  }

  private BuildImageConfigDTO.EnvironmentType mapEnvironmentEntityToDTO(CIBuildImageVmConfig.Environment env) {
    if (env == null) {
      return null;
    }

    return BuildImageConfigDTO.EnvironmentType.builder()
        .primary(mapImageDetailsListEntityToDTO(env.getPrimary()))
        .beta(mapImageDetailsListEntityToDTO(env.getBeta()))
        .build();
  }

  private BuildImageConfigDTO.EnvironmentType mapEnvironmentEntityToDTO(CIBuildImageVmConfig.Environment env,
      OSType osType, CIFeatureFlagService ciFeatureFlagService, String accountId) {
    if (ciFeatureFlagService == null) {
      log.info("AccountId: {}, Feature flag service is null, will use primary images", accountId);
    }
    if (env == null) {
      return null;
    }

    boolean useBetaImages = false;

    if (osType == OSType.Linux) {
      useBetaImages = ciFeatureFlagService.isEnabled(FeatureName.CI_ENABLE_HOSTED_BETA_IMAGES, accountId);
      log.info("AccountId: {}, OS Type: {}, {} enabled: {}", accountId, osType,
          FeatureName.CI_ENABLE_HOSTED_BETA_IMAGES, useBetaImages);

    } else if (osType == OSType.MacOS) {
      useBetaImages = ciFeatureFlagService.isEnabled(FeatureName.CI_ENABLE_MAC_HOSTED_BETA_IMAGES, accountId);
      log.info("AccountId: {}, OS Type: {}, {} enabled: {}", accountId, osType,
          FeatureName.CI_ENABLE_MAC_HOSTED_BETA_IMAGES, useBetaImages);
    } else {
      log.info("AccountId: {}, OS Type: {}, No matching beta feature flag for this OS type", accountId, osType);
    }

    return BuildImageConfigDTO.EnvironmentType.builder()
        .primary(useBetaImages ? mapImageDetailsListEntityToDTO(env.getBeta())
                               : mapImageDetailsListEntityToDTO(env.getPrimary()))
        .beta(mapImageDetailsListEntityToDTO(env.getBeta()))
        .build();
  }

  private List<BuildImageConfigDTO.ImageDetailsConfig> mapImageDetailsListEntityToDTO(
      List<CIBuildImageVmConfig.ImageDetailsConfig> entityList) {
    if (entityList == null) {
      return null;
    }

    return entityList.stream().map(entity -> mapImageDetailsEntityToDTO(entity)).collect(Collectors.toList());
  }

  private BuildImageConfigDTO.ImageDetailsConfig mapImageDetailsEntityToDTO(
      CIBuildImageVmConfig.ImageDetailsConfig entity) {
    if (entity == null) {
      return null;
    }

    return BuildImageConfigDTO.ImageDetailsConfig.builder()
        .version(entity.getVersion())
        .image(entity.getImage())
        .build();
  }
}