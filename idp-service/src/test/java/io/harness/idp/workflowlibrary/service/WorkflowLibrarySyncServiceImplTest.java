/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.workflowlibrary.service;

import static io.harness.rule.OwnerRule.DIPENDRA;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.response.GitFileResponse;
import io.harness.category.element.UnitTests;
import io.harness.connector.utils.HarnessCodeConnectorUtils;
import io.harness.delegate.beans.connector.HarnessConnectorDTO;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.workflowlibrary.config.WorkflowLibraryConfig;
import io.harness.idp.workflowlibrary.entity.WorkflowLibraryEntity;
import io.harness.idp.workflowlibrary.repositories.WorkflowLibraryRepository;
import io.harness.impl.scm.ScmGitProviderMapper;
import io.harness.product.ci.scm.proto.FileChange;
import io.harness.product.ci.scm.proto.FindFilesInBranchRequest;
import io.harness.product.ci.scm.proto.FindFilesInBranchResponse;
import io.harness.product.ci.scm.proto.Provider;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.rule.Owner;
import io.harness.service.ScmServiceClient;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class WorkflowLibrarySyncServiceImplTest extends CategoryTest {
  @Mock private WorkflowLibraryRepository repository;
  @Mock private ScmServiceClient scmServiceClient;
  @Mock private SCMGrpc.SCMBlockingStub scmBlockingStub;
  @Mock private HarnessCodeConnectorUtils harnessCodeConnectorUtils;
  @Mock private ScmGitProviderMapper scmGitProviderMapper;
  @Mock private HarnessConnectorDTO connectorDTO;

  private WorkflowLibraryConfig config;
  private HarnessCodeRepoConfig harnessCodeRepoConfig;
  private WorkflowLibrarySyncServiceImpl syncService;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    config = WorkflowLibraryConfig.builder()
                 .accountIdentifier("test-account")
                 .orgIdentifier("test-org")
                 .projectIdentifier("test-project")
                 .repoIdentifier("workflow-library")
                 .branch("main")
                 .build();
    harnessCodeRepoConfig = HarnessCodeRepoConfig.builder()
                                .apiUrl("http://localhost:3000")
                                .gitBaseUrl("http://localhost:3000/git")
                                .serviceClientSharedSecret("secret")
                                .build();
    syncService = new WorkflowLibrarySyncServiceImpl(repository, config, harnessCodeRepoConfig, scmServiceClient,
        scmBlockingStub, harnessCodeConnectorUtils, scmGitProviderMapper);
    when(harnessCodeConnectorUtils.getDummyHarnessCodeConnectorWithJwtAuth(
             any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(connectorDTO);
    when(connectorDTO.getSlug()).thenReturn("test-account/test-org/test-project/workflow-library/+");
    when(scmGitProviderMapper.mapToSCMGitProvider(any())).thenReturn(Provider.newBuilder().build());
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSyncOverwritesExistingVersion() {
    FindFilesInBranchResponse fileListResponse =
        FindFilesInBranchResponse.newBuilder()
            .addFile(FileChange.newBuilder().setPath("workflows/scaffold-service").build())
            .build();
    when(scmBlockingStub.findFilesInBranch(any(FindFilesInBranchRequest.class))).thenReturn(fileListResponse);

    GitFileResponse configResponse = GitFileResponse.builder()
                                         .content("name: Scaffold Service\nstable: \"1.0.0\"\ncategory: DEPLOYMENT")
                                         .statusCode(200)
                                         .build();
    GitFileResponse workflowYamlResponse =
        GitFileResponse.builder().content("workflow:\n  name: Scaffold").statusCode(200).build();
    when(scmServiceClient.getFile(any(), any(), any())).thenReturn(configResponse, workflowYamlResponse);

    WorkflowLibraryEntity existingEntity = WorkflowLibraryEntity.builder()
                                               .id("existing-id")
                                               .identifier("scaffold-service")
                                               .version("1.0.0")
                                               .isStable(true)
                                               .build();
    when(repository.findByIdentifierAndVersion("scaffold-service", "1.0.0")).thenReturn(existingEntity);

    syncService.syncFromGitRepository();

    verify(repository, times(1)).save(any(WorkflowLibraryEntity.class));
    verify(repository).deleteByIdentifierAndVersionNot("scaffold-service", "1.0.0");
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSyncCreatesNewVersion() {
    FindFilesInBranchResponse fileListResponse =
        FindFilesInBranchResponse.newBuilder()
            .addFile(FileChange.newBuilder().setPath("workflows/scaffold-service").build())
            .build();
    when(scmBlockingStub.findFilesInBranch(any(FindFilesInBranchRequest.class))).thenReturn(fileListResponse);

    GitFileResponse configResponse = GitFileResponse.builder()
                                         .content("name: Scaffold Service\nstable: \"1.0.0\"\n"
                                             + "description: Test workflow\ncategory: DEPLOYMENT")
                                         .statusCode(200)
                                         .build();
    GitFileResponse workflowYamlResponse =
        GitFileResponse.builder().content("workflow:\n  name: Scaffold").statusCode(200).build();
    when(scmServiceClient.getFile(any(), any(), any())).thenReturn(configResponse, workflowYamlResponse);

    when(repository.findByIdentifierAndVersion("scaffold-service", "1.0.0")).thenReturn(null);

    syncService.syncFromGitRepository();

    verify(repository, times(1)).save(any(WorkflowLibraryEntity.class));
    verify(repository).deleteByIdentifierAndVersionNot("scaffold-service", "1.0.0");
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSyncDeletesStaleVersions() {
    FindFilesInBranchResponse fileListResponse =
        FindFilesInBranchResponse.newBuilder()
            .addFile(FileChange.newBuilder().setPath("workflows/my-workflow").build())
            .build();
    when(scmBlockingStub.findFilesInBranch(any(FindFilesInBranchRequest.class))).thenReturn(fileListResponse);

    GitFileResponse configResponse = GitFileResponse.builder()
                                         .content("name: My Workflow\nstable: \"2.0.0\"\ncategory: DEPLOYMENT")
                                         .statusCode(200)
                                         .build();
    GitFileResponse workflowYamlResponse =
        GitFileResponse.builder().content("workflow:\n  name: My Workflow").statusCode(200).build();
    when(scmServiceClient.getFile(any(), any(), any())).thenReturn(configResponse, workflowYamlResponse);

    when(repository.findByIdentifierAndVersion("my-workflow", "2.0.0")).thenReturn(null);

    syncService.syncFromGitRepository();

    verify(repository).save(any(WorkflowLibraryEntity.class));
    verify(repository).deleteByIdentifierAndVersionNot("my-workflow", "2.0.0");
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSyncHandlesEmptyRepo() {
    FindFilesInBranchResponse fileListResponse = FindFilesInBranchResponse.newBuilder().build();
    when(scmBlockingStub.findFilesInBranch(any(FindFilesInBranchRequest.class))).thenReturn(fileListResponse);

    syncService.syncFromGitRepository();

    verify(repository, never()).save(any());
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSyncResolvesPipelines() {
    FindFilesInBranchResponse fileListResponse =
        FindFilesInBranchResponse.newBuilder()
            .addFile(FileChange.newBuilder().setPath("workflows/onboard-service").build())
            .build();
    when(scmBlockingStub.findFilesInBranch(any(FindFilesInBranchRequest.class))).thenReturn(fileListResponse);

    String workflowConfigYaml = "name: Onboard Service\nstable: \"1.0.0\"\n"
        + "description: Onboarding workflow\ncategory: SERVICE_LIFECYCLE";
    String workflowYaml = "workflow:\n  pipelineRef: OOTB_PIPELINE_REF:deploy-pipeline";
    String pipelineConfigYaml = "name: Deploy Pipeline\nstable: \"1.0.0\"";
    String pipelineYaml = "pipeline:\n  name: Deploy";

    when(scmServiceClient.getFile(any(), any(), any()))
        .thenReturn(GitFileResponse.builder().content(workflowConfigYaml).statusCode(200).build(),
            GitFileResponse.builder().content(workflowYaml).statusCode(200).build(),
            GitFileResponse.builder().content(pipelineConfigYaml).statusCode(200).build(),
            GitFileResponse.builder().content(pipelineConfigYaml).statusCode(200).build(),
            GitFileResponse.builder().content(pipelineYaml).statusCode(200).build());

    when(repository.findByIdentifierAndVersion("onboard-service", "1.0.0")).thenReturn(null);

    syncService.syncFromGitRepository();

    verify(repository).save(any(WorkflowLibraryEntity.class));
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSyncSkipsWorkflowWithNoStableVersion() {
    FindFilesInBranchResponse fileListResponse =
        FindFilesInBranchResponse.newBuilder()
            .addFile(FileChange.newBuilder().setPath("workflows/bad-workflow").build())
            .build();
    when(scmBlockingStub.findFilesInBranch(any(FindFilesInBranchRequest.class))).thenReturn(fileListResponse);

    GitFileResponse configResponse =
        GitFileResponse.builder().content("name: Bad Workflow\ncategory: DEPLOYMENT").statusCode(200).build();
    when(scmServiceClient.getFile(any(), any(), any())).thenReturn(configResponse);

    syncService.syncFromGitRepository();

    verify(repository, never()).save(any());
    verify(repository, never()).deleteByIdentifierAndVersionNot(any(), any());
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSyncPersistsStatusFromConfig() {
    FindFilesInBranchResponse fileListResponse =
        FindFilesInBranchResponse.newBuilder()
            .addFile(FileChange.newBuilder().setPath("workflows/preview-workflow").build())
            .build();
    when(scmBlockingStub.findFilesInBranch(any(FindFilesInBranchRequest.class))).thenReturn(fileListResponse);

    GitFileResponse configResponse =
        GitFileResponse.builder()
            .content("name: Preview Workflow\nstable: \"1.0.0\"\ncategory: DEPLOYMENT\nstatus: preview")
            .statusCode(200)
            .build();
    GitFileResponse workflowYamlResponse =
        GitFileResponse.builder().content("workflow:\n  name: Preview").statusCode(200).build();
    when(scmServiceClient.getFile(any(), any(), any())).thenReturn(configResponse, workflowYamlResponse);
    when(repository.findByIdentifierAndVersion("preview-workflow", "1.0.0")).thenReturn(null);

    syncService.syncFromGitRepository();

    ArgumentCaptor<WorkflowLibraryEntity> captor = ArgumentCaptor.forClass(WorkflowLibraryEntity.class);
    verify(repository).save(captor.capture());
    assertEquals(WorkflowLibraryEntity.STATUS_PREVIEW, captor.getValue().getStatus());
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSyncDefaultsToPreviewWhenStatusAbsent() {
    FindFilesInBranchResponse fileListResponse =
        FindFilesInBranchResponse.newBuilder()
            .addFile(FileChange.newBuilder().setPath("workflows/new-workflow").build())
            .build();
    when(scmBlockingStub.findFilesInBranch(any(FindFilesInBranchRequest.class))).thenReturn(fileListResponse);

    GitFileResponse configResponse = GitFileResponse.builder()
                                         .content("name: New Workflow\nstable: \"1.0.0\"\ncategory: DEPLOYMENT")
                                         .statusCode(200)
                                         .build();
    GitFileResponse workflowYamlResponse =
        GitFileResponse.builder().content("workflow:\n  name: New").statusCode(200).build();
    when(scmServiceClient.getFile(any(), any(), any())).thenReturn(configResponse, workflowYamlResponse);
    when(repository.findByIdentifierAndVersion("new-workflow", "1.0.0")).thenReturn(null);

    syncService.syncFromGitRepository();

    ArgumentCaptor<WorkflowLibraryEntity> captor = ArgumentCaptor.forClass(WorkflowLibraryEntity.class);
    verify(repository).save(captor.capture());
    assertEquals(WorkflowLibraryEntity.STATUS_PREVIEW, captor.getValue().getStatus());
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSyncDefaultsToPreviewWhenStatusInvalid() {
    FindFilesInBranchResponse fileListResponse =
        FindFilesInBranchResponse.newBuilder()
            .addFile(FileChange.newBuilder().setPath("workflows/bad-status-workflow").build())
            .build();
    when(scmBlockingStub.findFilesInBranch(any(FindFilesInBranchRequest.class))).thenReturn(fileListResponse);

    GitFileResponse configResponse =
        GitFileResponse.builder()
            .content("name: Bad Status\nstable: \"1.0.0\"\ncategory: DEPLOYMENT\nstatus: banana")
            .statusCode(200)
            .build();
    GitFileResponse workflowYamlResponse =
        GitFileResponse.builder().content("workflow:\n  name: Bad").statusCode(200).build();
    when(scmServiceClient.getFile(any(), any(), any())).thenReturn(configResponse, workflowYamlResponse);
    when(repository.findByIdentifierAndVersion("bad-status-workflow", "1.0.0")).thenReturn(null);

    syncService.syncFromGitRepository();

    ArgumentCaptor<WorkflowLibraryEntity> captor = ArgumentCaptor.forClass(WorkflowLibraryEntity.class);
    verify(repository).save(captor.capture());
    assertEquals(WorkflowLibraryEntity.STATUS_PREVIEW, captor.getValue().getStatus());
  }
}
