/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.pipeline.FileDownloadResponseDTO;
import io.harness.pms.pipeline.dto.FileMetadata;
import io.harness.steps.upload.RuntimeFileInputData;

import java.io.InputStream;
import java.util.Date;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public interface InputFileService {
  boolean deleteFile(String accountIdentifier, String planExecutionId, String nodeExecutionId, String fileName);

  void deleteFilesForAllExecutions(Set<String> planExecutionIds, Boolean retainPipelineExecutionDetailsAfterDelete);

  RuntimeFileInputData resumeExecution(String accountIdentifier, String planExecutionId, String nodeExecutionId);

  FileMetadata getMetadata(String accountIdentifier, String planExecutionId, String nodeExecutionId);

  FileDownloadResponseDTO getFile(
      String accountIdentifier, String planExecutionId, String nodeExecutionId, String fileName);

  FileDownloadResponseDTO getFile(String accountIdentifier, String filePath);

  void uploadFile(
      String accountIdentifier, String planExecutionId, String nodeExecutionId, String fileName, InputStream stream);

  void updateTTL(String planExecutionId, Date ttlDate);
}
