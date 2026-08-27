/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.licenseusage.mapper;

import static io.harness.ResourceClass.FLEX;
import static io.harness.ResourceClass.LARGE;
import static io.harness.ResourceClass.MEDIUM;
import static io.harness.ResourceClass.SMALL;
import static io.harness.ResourceClass.XLARGE;
import static io.harness.ResourceClass.XSMALL;
import static io.harness.ResourceClass.XXLARGE;
import static io.harness.ResourceClass.XXXLARGE;
import static io.harness.data.structure.HarnessStringUtils.nullIfEmpty;

import io.harness.ArchitectureType;
import io.harness.BuildInfraType;
import io.harness.ModuleType;
import io.harness.OSType;
import io.harness.eventsframework.schemas.platform.CILicenseUsageData;
import io.harness.eventsframework.schemas.platform.Developer;
import io.harness.eventsframework.schemas.platform.LicenseUsageEvent;
import io.harness.ng.core.licenseusage.dto.CILicenseUsageDataDTO;
import io.harness.ng.core.licenseusage.dto.CILicenseUsageDataDTO.CILicenseUsageDataDTOBuilder;
import io.harness.ng.core.licenseusage.dto.DeveloperDTO;
import io.harness.ng.core.licenseusage.dto.LicenseUsageDTO;
import io.harness.ng.core.licenseusage.dto.LicenseUsageDTO.LicenseUsageDTOBuilder;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class LicenseUsageProtoToRestDTOMapper {
  public LicenseUsageDTO toRestDTO(LicenseUsageEvent licenseUsageEventProtoDTO) {
    CILicenseUsageDataDTO ciLicenseUsageDataDTO = null;
    if (licenseUsageEventProtoDTO.hasCiLicenseUsageData()) {
      ciLicenseUsageDataDTO = getCILicenseUsageData(licenseUsageEventProtoDTO.getCiLicenseUsageData());
    }

    LicenseUsageDTOBuilder licenseUsageDTOBuilder =
        LicenseUsageDTO.builder()
            .accountIdentifier(licenseUsageEventProtoDTO.getAccountIdentifier())
            .orgIdentifier(licenseUsageEventProtoDTO.getOrgIdentifier())
            .projectIdentifier(licenseUsageEventProtoDTO.getProjectIdentifier())
            .parentUniqueId(nullIfEmpty(licenseUsageEventProtoDTO.getParentUniqueId()))
            .developer(getDeveloperDTO(licenseUsageEventProtoDTO.getDeveloper()))
            .pipelineIdentifier(licenseUsageEventProtoDTO.getPipelineIdentifier())
            .stepIdentifier(licenseUsageEventProtoDTO.getStepIdentifier())
            .stageIdentifier(licenseUsageEventProtoDTO.getStageIdentifier())
            .moduleUsageData(ciLicenseUsageDataDTO);

    switch (licenseUsageEventProtoDTO.getModuleName()) {
      case MODULE_NAME_CI:
        licenseUsageDTOBuilder.moduleType(ModuleType.CI);
        break;
      case MODULE_NAME_IACM:
        licenseUsageDTOBuilder.moduleType(ModuleType.IACM);
        break;
      case MODULE_NAME_IDP:
        licenseUsageDTOBuilder.moduleType(ModuleType.IDP);
        break;
      case MODULE_NAME_STO:
        licenseUsageDTOBuilder.moduleType(ModuleType.STO);
        break;
      default:
        break;
    }

    return licenseUsageDTOBuilder.build();
  }

  private CILicenseUsageDataDTO getCILicenseUsageData(CILicenseUsageData ciLicenseUsageDataProto) {
    CILicenseUsageDataDTOBuilder builder = CILicenseUsageDataDTO.builder();
    builder.buildMinutes(ciLicenseUsageDataProto.getBuildMinutes());
    builder.lastBuildTimestamp(ciLicenseUsageDataProto.getLastBuildTimestamp());

    if (ciLicenseUsageDataProto.getArchType() != null) {
      switch (ciLicenseUsageDataProto.getArchType()) {
        case ARCHITECTURE_TYPE_AMD64:
          builder.architectureType(ArchitectureType.AMD64);
          break;
        case ARCHITECTURE_TYPE_ARM64:
          builder.architectureType(ArchitectureType.ARM64);
          break;
        default:
          log.error("Unsupported CI Architecture type {}", ciLicenseUsageDataProto.getArchType());
          break;
      }
      if (ciLicenseUsageDataProto.getArchType().equals(
              io.harness.eventsframework.schemas.platform.ArchitectureType.ARCHITECTURE_TYPE_AMD64)) {
        builder.architectureType(ArchitectureType.AMD64);
      }
    }
    if (ciLicenseUsageDataProto.getOsType() != null) {
      switch (ciLicenseUsageDataProto.getOsType()) {
        case OSTYPE_LINUX:
          builder.osType(OSType.LINUX);
          break;
        case OSTYPE_WINDOWS:
          builder.osType(OSType.WINDOWS);
          break;
        case OSTYPE_MAC:
          builder.osType(OSType.MACOS);
          break;
        default:
          log.error("Unsupported OS type {}", ciLicenseUsageDataProto.getOsType());
          break;
      }
    }
    if (ciLicenseUsageDataProto.getBuildInfraType() != null) {
      switch (ciLicenseUsageDataProto.getBuildInfraType()) {
        case BUILD_INFRA_TYPE_CLOUD:
          builder.buildInfraType(BuildInfraType.CLOUD);
          break;
        case BUILD_INFRA_TYPE_DOCKER:
          builder.buildInfraType(BuildInfraType.DOCKER);
          break;
        case BUILD_INFRA_TYPE_KUBERNETES:
          builder.buildInfraType(BuildInfraType.KUBERNETES);
          break;
        case BUILD_INFRA_TYPE_VIRTUAL_MACHINE:
          builder.buildInfraType(BuildInfraType.VIRTUAL_MACHINE);
          break;
        case BUILD_INFRA_TYPE_UNSPECIFIED:
          throw new RuntimeException(
              "Build InfraType was not specified for CILicense, can not compute the license usage for this event.");
        default:
          log.error("Unsupported Build Infra type {}", ciLicenseUsageDataProto.getBuildInfraType());
          break;
      }
    }
    if (!ciLicenseUsageDataProto.getDevelopersList().isEmpty()) {
      List<io.harness.Developer> harnessDevelopers = new ArrayList<>();
      for (Developer developer : ciLicenseUsageDataProto.getDevelopersList()) {
        io.harness.Developer harnessDeveloper = new io.harness.Developer(developer.getEmail(), developer.getName());
        harnessDevelopers.add(harnessDeveloper);
      }
      builder.buildDevelopers(harnessDevelopers);
    }
    if (ciLicenseUsageDataProto.getResourceClass() != null) {
      switch (ciLicenseUsageDataProto.getResourceClass()) {
        case RESOURCE_CLASS_LARGE:
          builder.resourceClass(LARGE);
          break;
        case RESOURCE_CLASS_MEDIUM:
          builder.resourceClass(MEDIUM);
          break;
        case RESOURCE_CLASS_SMALL:
          builder.resourceClass(SMALL);
          break;
        case RESOURCE_CLASS_FLEX:
          builder.resourceClass(FLEX);
          break;
        case RESOURCE_CLASS_XLARGE:
          builder.resourceClass(XLARGE);
          break;
        case RESOURCE_CLASS_XXLARGE:
          builder.resourceClass(XXLARGE);
          break;
        case RESOURCE_CLASS_XXXLARGE:
          builder.resourceClass(XXXLARGE);
          break;
        case RESOURCE_CLASS_XSMALL:
          builder.resourceClass(XSMALL);
          break;
        default:
          log.error("Unsupported Resource Class {}", ciLicenseUsageDataProto.getResourceClass());
          break;
      }
    }
    if (ciLicenseUsageDataProto.getNetworkEgressMB() != 0) {
      builder.networkEgressMB(ciLicenseUsageDataProto.getNetworkEgressMB());
    }
    if (ciLicenseUsageDataProto.getNetworkIngressMB() != 0) {
      builder.networkEgressMB(ciLicenseUsageDataProto.getNetworkIngressMB());
    }
    if (ciLicenseUsageDataProto.getStorageConsumedMB() != 0) {
      builder.storageConsumedMB(ciLicenseUsageDataProto.getStorageConsumedMB());
    }
    return builder.build();
  }

  private DeveloperDTO getDeveloperDTO(Developer developerProto) {
    return DeveloperDTO.builder().email(developerProto.getEmail()).name(developerProto.getName()).build();
  }
}
