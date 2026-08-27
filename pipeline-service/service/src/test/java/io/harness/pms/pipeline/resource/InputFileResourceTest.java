/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.pipeline.FileDeleteResponseDTO;
import io.harness.pms.pipeline.FileMetadataResponseDTO;
import io.harness.pms.pipeline.FileUploadResumeExecutionResponseDTO;
import io.harness.pms.pipeline.dto.FileInfoDTO;
import io.harness.pms.pipeline.dto.FileMetadata;
import io.harness.pms.pipeline.service.InputFileService;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.steps.upload.FileInfo;
import io.harness.steps.upload.RuntimeFileInputData;
import io.harness.utils.PmsFeatureFlagService;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class InputFileResourceTest extends CategoryTest {
  InputFileResourceImpl inputFileResource;
  @Mock InputFileService inputFileResourceService;
  @Mock AccessControlClient accessControlClient;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock NodeExecutionService nodeExecutionService;
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_ID = "org_id";
  private final String PROJECT_ID = "project_id";
  private final String PIPELINE_ID = "pipeline_id";
  private final String PLAN_EXECUTION_ID = "plan_execution_id";
  private final String NODE_EXECUTION_ID = "node_execution_id";

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    inputFileResource = new InputFileResourceImpl(inputFileResourceService);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetMetadata() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_FILE_UPLOAD_AS_RUNTIME_INPUT))
        .thenReturn(true);
    FileMetadata fileMetadata =
        FileMetadata.builder()
            .accountIdentifier(ACCOUNT_ID)
            .planExecutionId(PLAN_EXECUTION_ID)
            .nodeExecutionId(NODE_EXECUTION_ID)
            .fileInfos(Arrays.asList(FileInfoDTO.builder().filePath("account_id/inputFile.txt").size(5L).build()))
            .build();
    when(inputFileResourceService.getMetadata(any(), any(), any())).thenReturn(fileMetadata);
    Map<String, String> abstractions = new HashMap<>();
    abstractions.put(SetupAbstractionKeys.accountId, ACCOUNT_ID);
    abstractions.put(SetupAbstractionKeys.orgIdentifier, ORG_ID);
    abstractions.put(SetupAbstractionKeys.projectIdentifier, PROJECT_ID);
    when(nodeExecutionService.getWithFieldsIncluded(NODE_EXECUTION_ID, NodeProjectionUtils.withAmbiance))
        .thenReturn(NodeExecution.builder()
                        .ambiance(Ambiance.newBuilder()
                                      .setPlanExecutionId(PLAN_EXECUTION_ID)
                                      .putAllSetupAbstractions(abstractions)
                                      .build())
                        .build());
    ResponseDTO<FileMetadataResponseDTO> responseDTO =
        inputFileResource.getFileMetadata(ACCOUNT_ID, PLAN_EXECUTION_ID, NODE_EXECUTION_ID);
    assertThat(responseDTO.getData().getAccountIdentifier()).isNotNull();
    assertEquals("inputFile.txt", responseDTO.getData().getFileInfoResponseDTOS().get(0).getFileName());
    verify(inputFileResourceService, times(1)).getMetadata(ACCOUNT_ID, PLAN_EXECUTION_ID, NODE_EXECUTION_ID);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testDeleteFile() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_FILE_UPLOAD_AS_RUNTIME_INPUT))
        .thenReturn(true);
    NodeExecution nodeExecution = setUpValidationData();
    when(nodeExecutionService.getWithFieldsIncluded(NODE_EXECUTION_ID, NodeProjectionUtils.withAmbiance))
        .thenReturn(nodeExecution);
    when(inputFileResourceService.deleteFile(ACCOUNT_ID, PLAN_EXECUTION_ID, NODE_EXECUTION_ID, "inputFile.txt"))
        .thenReturn(true);
    ResponseDTO<FileDeleteResponseDTO> responseDTO =
        inputFileResource.deleteFile(ACCOUNT_ID, PLAN_EXECUTION_ID, NODE_EXECUTION_ID, "inputFile.txt");

    assertThat(responseDTO.getData().isSuccess()).isTrue();
    verify(inputFileResourceService, times(1))
        .deleteFile(ACCOUNT_ID, PLAN_EXECUTION_ID, NODE_EXECUTION_ID, "inputFile.txt");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testUploadFile_InvalidExtension() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_FILE_UPLOAD_AS_RUNTIME_INPUT))
        .thenReturn(true);
    NodeExecution nodeExecution = setUpValidationData();
    when(nodeExecutionService.getWithFieldsIncluded(NODE_EXECUTION_ID, NodeProjectionUtils.withAmbiance))
        .thenReturn(nodeExecution);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> inputFileResource.uploadFile(
                            ACCOUNT_ID, PLAN_EXECUTION_ID, NODE_EXECUTION_ID, "invalid_file.xyz", null));
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testResumeExecution() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_FILE_UPLOAD_AS_RUNTIME_INPUT))
        .thenReturn(true);
    NodeExecution nodeExecution = setUpValidationData();
    when(nodeExecutionService.getWithFieldsIncluded(NODE_EXECUTION_ID, NodeProjectionUtils.withAmbiance))
        .thenReturn(nodeExecution);

    when(inputFileResourceService.resumeExecution(ACCOUNT_ID, PLAN_EXECUTION_ID, NODE_EXECUTION_ID))
        .thenReturn(
            RuntimeFileInputData.builder()
                .nodeExecutionId(NODE_EXECUTION_ID)
                .fileInfos(Collections.singletonList(FileInfo.builder().filePath("account_id/inputFile.txt").build()))
                .submittedBy(EmbeddedUser.builder().name("testUser").email("test@harness.io").build())
                .build());

    ResponseDTO<FileUploadResumeExecutionResponseDTO> responseDTO =
        inputFileResource.resumeExecution(ACCOUNT_ID, PLAN_EXECUTION_ID, NODE_EXECUTION_ID);

    assertThat(responseDTO).isNotNull();
    assertEquals(NODE_EXECUTION_ID, responseDTO.getData().getNodeExecutionId());
    assertEquals("test@harness.io", responseDTO.getData().getSubmittedBy().getEmail());
    verify(inputFileResourceService, times(1)).resumeExecution(ACCOUNT_ID, PLAN_EXECUTION_ID, NODE_EXECUTION_ID);
  }

  private NodeExecution setUpValidationData() {
    Map<String, String> abstractions = new HashMap<>();
    abstractions.put(SetupAbstractionKeys.accountId, ACCOUNT_ID);
    abstractions.put(SetupAbstractionKeys.orgIdentifier, ORG_ID);
    abstractions.put(SetupAbstractionKeys.projectIdentifier, PROJECT_ID);

    return NodeExecution.builder()
        .ambiance(
            Ambiance.newBuilder().setPlanExecutionId(PLAN_EXECUTION_ID).putAllSetupAbstractions(abstractions).build())
        .build();
  }
}
