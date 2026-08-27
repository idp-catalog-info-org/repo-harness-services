/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.inputfile.InputFileResource;
import io.harness.pms.pipeline.FileDeleteResponseDTO;
import io.harness.pms.pipeline.FileDownloadResponseDTO;
import io.harness.pms.pipeline.FileMetadataResponseDTO;
import io.harness.pms.pipeline.FileUploadResponseDTO;
import io.harness.pms.pipeline.FileUploadResumeExecutionResponseDTO;
import io.harness.pms.pipeline.dto.FileMetadata;
import io.harness.pms.pipeline.mappers.InputFileMapper;
import io.harness.pms.pipeline.service.InputFileService;
import io.harness.steps.upload.FileInfo;
import io.harness.steps.upload.RuntimeFileInputData;

import com.google.inject.Inject;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@PipelineServiceAuth
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class InputFileResourceImpl implements InputFileResource {
  @Inject private InputFileService inputFileResourceService;

  public static final List<String> ALLOWED_FILE_EXTENSIONS = List.of(
      "jpg", "jpeg", "png", "pdf", "xls", "csv", "xlsx", "txt", "json", "yaml", "xml", "html", "yml", "doc", "docx");

  @Override
  public ResponseDTO<FileDeleteResponseDTO> deleteFile(
      String accountIdentifier, String planExecutionId, String nodeExecutionId, String fileName) {
    return ResponseDTO.newResponse(
        FileDeleteResponseDTO.builder()
            .success(inputFileResourceService.deleteFile(accountIdentifier, planExecutionId, nodeExecutionId, fileName))
            .build());
  }

  @Override
  public ResponseDTO<FileMetadataResponseDTO> getFileMetadata(
      String accountIdentifier, String planExecutionId, String nodeExecutionId) {
    FileMetadata fileMetadata =
        inputFileResourceService.getMetadata(accountIdentifier, planExecutionId, nodeExecutionId);
    return ResponseDTO.newResponse(InputFileMapper.toFileMetadataResponseDTO(fileMetadata));
  }

  @Override
  public Response getFile(String accountIdentifier, String planExecutionId, String nodeExecutionId, String fileName) {
    FileDownloadResponseDTO fileDownloadResponse =
        inputFileResourceService.getFile(accountIdentifier, planExecutionId, nodeExecutionId, fileName);
    return Response.ok(fileDownloadResponse.getOutput()).type(fileDownloadResponse.getMimeType()).build();
  }

  @Override
  public ResponseDTO<FileUploadResumeExecutionResponseDTO> resumeExecution(
      String accountIdentifier, String planExecutionId, String nodeExecutionId) {
    RuntimeFileInputData runtimeFileInputData =
        inputFileResourceService.resumeExecution(accountIdentifier, planExecutionId, nodeExecutionId);
    List<String> filePaths =
        runtimeFileInputData.getFileInfos().stream().map(FileInfo::getFilePath).collect(Collectors.toList());
    return ResponseDTO.newResponse(FileUploadResumeExecutionResponseDTO.builder()
                                       .nodeExecutionId(runtimeFileInputData.getNodeExecutionId())
                                       .submittedBy(runtimeFileInputData.getSubmittedBy())
                                       .filePaths(filePaths)
                                       .build());
  }

  @Override
  public ResponseDTO<FileUploadResponseDTO> uploadFile(
      String accountIdentifier, String planExecutionId, String nodeExecutionId, String fileName, InputStream content) {
    try {
      validateFileExtension(fileName);
      inputFileResourceService.uploadFile(accountIdentifier, planExecutionId, nodeExecutionId, fileName, content);
      return ResponseDTO.newResponse(FileUploadResponseDTO.builder().success(true).build());
    } catch (WingsException wingsException) {
      throw wingsException;
    } catch (Exception exception) {
      log.error(String.format("Faced unexpected error while uploading runtime input file for nodeExecutionId: [%s]",
                    nodeExecutionId),
          exception);
      throw new InternalServerErrorException(
          "Unexpected error occurred while uploading file. Please contact Harness Support.");
    }
  }

  @Override
  public Response downloadFileUsingFilePath(String accountIdentifier, String filePath) {
    FileDownloadResponseDTO fileDownloadResponse = inputFileResourceService.getFile(accountIdentifier, filePath);
    return Response.ok(fileDownloadResponse.getOutput()).type(fileDownloadResponse.getMimeType()).build();
  }

  private void validateFileExtension(String fileName) {
    if (isNotEmpty(fileName)) {
      String fileExtension = FilenameUtils.getExtension(fileName).toLowerCase();
      if (!ALLOWED_FILE_EXTENSIONS.contains(fileExtension)) {
        log.error(String.format("Received invalid file extension [%s] in the request", fileExtension));
        throw new InvalidRequestException(
            String.format("Received invalid file extension [%s] in the request. Allowed file extensions are [%s]",
                fileExtension, String.join(", ", ALLOWED_FILE_EXTENSIONS)));
      }
    }
  }
}
