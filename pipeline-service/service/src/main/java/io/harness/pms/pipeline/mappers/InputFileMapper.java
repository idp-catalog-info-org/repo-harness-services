/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.EmbeddedUser;
import io.harness.pms.pipeline.FileInfoResponseDTO;
import io.harness.pms.pipeline.FileMetadataResponseDTO;
import io.harness.pms.pipeline.UserInfoResponseDTO;
import io.harness.pms.pipeline.dto.FileInfoDTO;
import io.harness.pms.pipeline.dto.FileMetadata;
import io.harness.pms.utils.RuntimeInputFileUtils;
import io.harness.steps.upload.FileInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@UtilityClass
public class InputFileMapper {
  public FileMetadataResponseDTO toFileMetadataResponseDTO(FileMetadata fileMetadata) {
    if (fileMetadata == null) {
      return FileMetadataResponseDTO.builder().build();
    }
    return FileMetadataResponseDTO.builder()
        .accountIdentifier(fileMetadata.getAccountIdentifier())
        .planExecutionId(fileMetadata.getPlanExecutionId())
        .nodeExecutionId(fileMetadata.getNodeExecutionId())
        .fileInfoResponseDTOS(buildFileInfoResponseDTO(fileMetadata.getFileInfos()))
        .build();
  }

  private List<FileInfoResponseDTO> buildFileInfoResponseDTO(List<FileInfoDTO> fileInfoDTOS) {
    if (isEmpty(fileInfoDTOS)) {
      return new ArrayList<>();
    } else {
      return fileInfoDTOS.stream().map(InputFileMapper::getFileInfoResponseDTO).collect(Collectors.toList());
    }
  }

  private FileInfoResponseDTO getFileInfoResponseDTO(FileInfoDTO fileInfoDTO) {
    if (fileInfoDTO == null) {
      return FileInfoResponseDTO.builder().build();
    }
    return FileInfoResponseDTO.builder()
        .fileName(RuntimeInputFileUtils.extractFileNameFromFilePath(fileInfoDTO.getFilePath()))
        .size(fileInfoDTO.getSize())
        .userInfo(getUserInfoResponseDTO(fileInfoDTO.getUploadedBy()))
        .build();
  }

  private UserInfoResponseDTO getUserInfoResponseDTO(EmbeddedUser embeddedUser) {
    if (embeddedUser == null) {
      return UserInfoResponseDTO.builder().build();
    }
    return UserInfoResponseDTO.builder().emailId(embeddedUser.getEmail()).build();
  }

  public List<FileInfoDTO> getFileInfoResponse(List<FileInfo> fileInfos) {
    if (isEmpty(fileInfos)) {
      return new ArrayList<>();
    }
    return fileInfos.stream().map(InputFileMapper::getFileInfoDTO).collect(Collectors.toList());
  }

  private FileInfoDTO getFileInfoDTO(FileInfo fileInfo) {
    if (fileInfo == null) {
      return FileInfoDTO.builder().build();
    }
    return FileInfoDTO.builder()
        .filePath(fileInfo.getFilePath())
        .size(fileInfo.getSize())
        .uploadedBy(fileInfo.getUploadedBy())
        .build();
  }
}
