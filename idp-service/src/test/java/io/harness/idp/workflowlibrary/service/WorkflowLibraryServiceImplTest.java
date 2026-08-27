/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.workflowlibrary.service;

import static io.harness.rule.OwnerRule.DIPENDRA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.repositories.EntityLinkRepository;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.workflowlibrary.entity.WorkflowAdminInput;
import io.harness.idp.workflowlibrary.entity.WorkflowLibraryEntity;
import io.harness.idp.workflowlibrary.entity.WorkflowPipelineSnapshot;
import io.harness.idp.workflowlibrary.repositories.WorkflowLibraryRepository;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.WorkflowInstallResponse;
import io.harness.springdata.TransactionHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;

@OwnedBy(HarnessTeam.IDP)
public class WorkflowLibraryServiceImplTest extends CategoryTest {
  @Mock private WorkflowLibraryRepository repository;
  @Mock private PipelineServiceClient pipelineServiceClient;
  @Mock private CatalogService catalogService;
  @Mock private IDPGitXHelper idpGitXHelper;
  @Mock private AccessControlClient accessControlClient;
  @Mock private AccountClient accountClient;
  @Mock private EntityLinkRepository entityLinkRepository;
  @Mock private TransactionHelper transactionHelper;
  @Mock private Call mockCall;

  private WorkflowLibraryServiceImpl service;

  private static final String ACCOUNT_ID = "test-account";
  private static final String PIPELINE_ORG_ID = "test-org";
  private static final String PIPELINE_PROJECT_ID = "test-project";
  private static final String WORKFLOW_ORG_ID = "workflow-org";
  private static final String WORKFLOW_PROJECT_ID = "workflow-project";
  private static final String INSTANCE_ID = "my_instance";

  private static final String NG_BASE_URL = "https://app.harness.io";

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    SecurityContextBuilder.setContext(new UserPrincipal("user-id", "user@harness.io", "user", ACCOUNT_ID));
    service = new WorkflowLibraryServiceImpl(repository, pipelineServiceClient, catalogService, idpGitXHelper,
        accessControlClient, accountClient, entityLinkRepository, transactionHelper, NG_BASE_URL);
    when(catalogService.getEntity(any(), any(), any(), any(), eq(false), eq(false), eq(false)))
        .thenThrow(new EntityNotFoundException("not found"));
  }

  @After
  public void tearDown() {
    SecurityContextBuilder.unsetCompleteContext();
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testListWorkflowsNoCategory() {
    List<WorkflowLibraryEntity> expected = Collections.singletonList(buildEntity("workflow-1", "1.0.0"));
    when(repository.findByIsStableTrueAndDeprecatedFalse()).thenReturn(expected);
    when(accountClient.isFeatureFlagEnabled(any(), any())).thenReturn(mockCall);

    try (MockedStatic<CGRestUtils> cgMock = mockStatic(CGRestUtils.class)) {
      cgMock.when(() -> CGRestUtils.getResponse(any())).thenReturn(true);

      List<WorkflowLibraryEntity> result = service.listWorkflows(ACCOUNT_ID, null);

      assertEquals(1, result.size());
      assertEquals("workflow-1", result.get(0).getIdentifier());
      verify(repository).findByIsStableTrueAndDeprecatedFalse();
    }
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testListWorkflowsWithCategory() {
    List<WorkflowLibraryEntity> expected = Collections.singletonList(buildEntity("workflow-1", "1.0.0"));
    when(repository.findByCategoryAndIsStableTrueAndDeprecatedFalse("DEPLOYMENT")).thenReturn(expected);
    when(accountClient.isFeatureFlagEnabled(any(), any())).thenReturn(mockCall);

    try (MockedStatic<CGRestUtils> cgMock = mockStatic(CGRestUtils.class)) {
      cgMock.when(() -> CGRestUtils.getResponse(any())).thenReturn(true);

      List<WorkflowLibraryEntity> result = service.listWorkflows(ACCOUNT_ID, "DEPLOYMENT");

      assertEquals(1, result.size());
      verify(repository).findByCategoryAndIsStableTrueAndDeprecatedFalse("DEPLOYMENT");
    }
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testListWorkflowsHidesPreviewWhenFFDisabled() {
    List<WorkflowLibraryEntity> gaWorkflows = Collections.singletonList(buildEntity("workflow-1", "1.0.0"));
    when(repository.findByStatusAndIsStableTrueAndDeprecatedFalse(WorkflowLibraryEntity.STATUS_GA))
        .thenReturn(gaWorkflows);
    when(accountClient.isFeatureFlagEnabled(any(), any())).thenReturn(mockCall);

    try (MockedStatic<CGRestUtils> cgMock = mockStatic(CGRestUtils.class)) {
      cgMock.when(() -> CGRestUtils.getResponse(any())).thenReturn(false);

      List<WorkflowLibraryEntity> result = service.listWorkflows(ACCOUNT_ID, null);

      assertEquals(1, result.size());
      verify(repository).findByStatusAndIsStableTrueAndDeprecatedFalse(WorkflowLibraryEntity.STATUS_GA);
      verify(repository, never()).findByIsStableTrueAndDeprecatedFalse();
    }
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testGetWorkflowNotFound() {
    when(repository.findByIdentifierAndIsStableTrue("non-existent")).thenReturn(null);
    when(accountClient.isFeatureFlagEnabled(any(), any())).thenReturn(mockCall);

    try (MockedStatic<CGRestUtils> cgMock = mockStatic(CGRestUtils.class)) {
      cgMock.when(() -> CGRestUtils.getResponse(any())).thenReturn(true);
      assertThrows(InvalidRequestException.class, () -> service.getWorkflow(ACCOUNT_ID, "non-existent"));
    }
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testGetWorkflowFound() {
    WorkflowLibraryEntity entity = buildEntity("my-workflow", "1.0.0");
    when(repository.findByIdentifierAndIsStableTrue("my-workflow")).thenReturn(entity);
    when(accountClient.isFeatureFlagEnabled(any(), any())).thenReturn(mockCall);

    try (MockedStatic<CGRestUtils> cgMock = mockStatic(CGRestUtils.class)) {
      cgMock.when(() -> CGRestUtils.getResponse(any())).thenReturn(true);

      WorkflowLibraryEntity result = service.getWorkflow(ACCOUNT_ID, "my-workflow");

      assertEquals("my-workflow", result.getIdentifier());
    }
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testGetWorkflowHidesPreviewWhenFFDisabled() {
    WorkflowLibraryEntity previewEntity = WorkflowLibraryEntity.builder()
                                              .identifier("preview-workflow")
                                              .version("1.0.0")
                                              .status(WorkflowLibraryEntity.STATUS_PREVIEW)
                                              .isStable(true)
                                              .deprecated(false)
                                              .build();
    when(repository.findByIdentifierAndIsStableTrue("preview-workflow")).thenReturn(previewEntity);
    when(accountClient.isFeatureFlagEnabled(any(), any())).thenReturn(mockCall);

    try (MockedStatic<CGRestUtils> cgMock = mockStatic(CGRestUtils.class)) {
      cgMock.when(() -> CGRestUtils.getResponse(any())).thenReturn(false);
      assertThrows(InvalidRequestException.class, () -> service.getWorkflow(ACCOUNT_ID, "preview-workflow"));
    }
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testGetWorkflowVersionNotFound() {
    when(repository.findByIdentifierAndVersion("workflow-1", "2.0.0")).thenReturn(null);
    when(accountClient.isFeatureFlagEnabled(any(), any())).thenReturn(mockCall);

    try (MockedStatic<CGRestUtils> cgMock = mockStatic(CGRestUtils.class)) {
      cgMock.when(() -> CGRestUtils.getResponse(any())).thenReturn(true);
      assertThrows(InvalidRequestException.class, () -> service.getWorkflowVersion(ACCOUNT_ID, "workflow-1", "2.0.0"));
    }
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testInstallSuccess() {
    WorkflowPipelineSnapshot pipeline = WorkflowPipelineSnapshot.builder()
                                            .identifier("scaffold-pipeline")
                                            .name("Scaffold Pipeline")
                                            .pipelineYaml("pipeline:\n  orgIdentifier: {{orgIdentifier}}\n"
                                                + "  connectorRef: OOTB_ADMIN:git_connector_ref")
                                            .adminInputs(Collections.emptyList())
                                            .build();
    WorkflowLibraryEntity entity =
        WorkflowLibraryEntity.builder()
            .identifier("scaffold-workflow")
            .version("1.0.0")
            .workflowYaml(
                "identifier: scaffold_workflow\nworkflow:\n  pipelineRef: OOTB_PIPELINE_REF:scaffold-pipeline")
            .pipelines(Collections.singletonList(pipeline))
            .adminInputs(Collections.emptyList())
            .build();
    when(repository.findByIdentifierAndVersion("scaffold-workflow", "1.0.0")).thenReturn(entity);

    try (MockedStatic<NGRestUtils> ngRestUtilsMock = mockStatic(NGRestUtils.class)) {
      ngRestUtilsMock.when(() -> NGRestUtils.getGeneralResponse(any())).thenReturn(null);
      when(pipelineServiceClient.getPipeline(
               any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
          .thenThrow(new InvalidRequestException("not found"));
      when(pipelineServiceClient.createPipeline(any(), eq(PIPELINE_ORG_ID), eq(PIPELINE_PROJECT_ID), eq(ACCOUNT_ID)))
          .thenReturn(mockCall);

      Map<String, String> adminInputValues = new HashMap<>();
      adminInputValues.put("git_connector_ref", "account.myGitConnector");
      WorkflowInstallResponse response =
          service.install(ACCOUNT_ID, PIPELINE_ORG_ID, PIPELINE_PROJECT_ID, WORKFLOW_ORG_ID, WORKFLOW_PROJECT_ID,
              "scaffold-workflow", "1.0.0", INSTANCE_ID, "My Scaffold", adminInputValues, null, null);

      assertEquals(WorkflowInstallResponse.StatusEnum.SUCCESS, response.getStatus());
      assertEquals("scaffold-workflow", response.getWorkflowIdentifier());
      assertEquals(1, response.getPipelineIdentifiers().size());
      verify(catalogService)
          .createEntity(eq(ACCOUNT_ID), eq(WORKFLOW_ORG_ID), eq(WORKFLOW_PROJECT_ID), eq(false), eq(false), any());
    }
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testInstallValidationFailsMissingRequiredInput() {
    WorkflowAdminInput requiredInput = WorkflowAdminInput.builder().key("github_connector").required(true).build();
    WorkflowLibraryEntity entity = WorkflowLibraryEntity.builder()
                                       .identifier("workflow-1")
                                       .version("1.0.0")
                                       .adminInputs(Collections.singletonList(requiredInput))
                                       .pipelines(Collections.emptyList())
                                       .workflowYaml("yaml")
                                       .build();
    when(repository.findByIdentifierAndVersion("workflow-1", "1.0.0")).thenReturn(entity);

    assertThrows(InvalidRequestException.class,
        ()
            -> service.install(ACCOUNT_ID, PIPELINE_ORG_ID, PIPELINE_PROJECT_ID, WORKFLOW_ORG_ID, WORKFLOW_PROJECT_ID,
                "workflow-1", "1.0.0", INSTANCE_ID, null, new HashMap<>(), null, null));
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testInstallUsesStableVersionWhenVersionNull() {
    WorkflowLibraryEntity entity = WorkflowLibraryEntity.builder()
                                       .identifier("workflow-1")
                                       .version("1.0.0")
                                       .workflowYaml("yaml")
                                       .pipelines(Collections.emptyList())
                                       .adminInputs(Collections.emptyList())
                                       .build();
    when(repository.findByIdentifierAndIsStableTrue("workflow-1")).thenReturn(entity);

    try (MockedStatic<NGRestUtils> ngRestUtilsMock = mockStatic(NGRestUtils.class)) {
      ngRestUtilsMock.when(() -> NGRestUtils.getGeneralResponse(any())).thenReturn(null);

      WorkflowInstallResponse response = service.install(ACCOUNT_ID, PIPELINE_ORG_ID, PIPELINE_PROJECT_ID,
          WORKFLOW_ORG_ID, WORKFLOW_PROJECT_ID, "workflow-1", null, INSTANCE_ID, null, new HashMap<>(), null, null);

      assertEquals(WorkflowInstallResponse.StatusEnum.SUCCESS, response.getStatus());
      verify(repository).findByIdentifierAndIsStableTrue("workflow-1");
      verify(repository, never()).findByIdentifierAndVersion(any(), any());
    }
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testInstallFailsWhenUserLacksPipelineCreatePermission() {
    WorkflowLibraryEntity entity = WorkflowLibraryEntity.builder()
                                       .identifier("workflow-1")
                                       .version("1.0.0")
                                       .workflowYaml("yaml")
                                       .pipelines(Collections.emptyList())
                                       .adminInputs(Collections.emptyList())
                                       .build();
    when(repository.findByIdentifierAndVersion("workflow-1", "1.0.0")).thenReturn(entity);

    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), eq(ResourceScope.of(ACCOUNT_ID, WORKFLOW_ORG_ID, WORKFLOW_PROJECT_ID)),
            eq(Resource.of("IDP_WORKFLOW", null)), eq("idp_workflow_edit"), any());
    doThrow(new InvalidRequestException("Missing permission to create pipelines in the specified project"))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), eq(ResourceScope.of(ACCOUNT_ID, PIPELINE_ORG_ID, PIPELINE_PROJECT_ID)),
            eq(Resource.of("PIPELINE", null)), eq("core_pipeline_edit"), any());

    assertThrows(InvalidRequestException.class,
        ()
            -> service.install(ACCOUNT_ID, PIPELINE_ORG_ID, PIPELINE_PROJECT_ID, WORKFLOW_ORG_ID, WORKFLOW_PROJECT_ID,
                "workflow-1", "1.0.0", INSTANCE_ID, null, new HashMap<>(), null, null));
    verify(pipelineServiceClient, never()).createPipeline(any(), any(), any(), any());
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testInstallPipelineCreationFailureThrowsException() {
    WorkflowPipelineSnapshot pipeline = WorkflowPipelineSnapshot.builder()
                                            .identifier("failing-pipeline")
                                            .name("Failing Pipeline")
                                            .pipelineYaml("pipeline: yaml")
                                            .adminInputs(Collections.emptyList())
                                            .build();
    WorkflowLibraryEntity entity = WorkflowLibraryEntity.builder()
                                       .identifier("workflow-1")
                                       .version("1.0.0")
                                       .workflowYaml("yaml")
                                       .pipelines(Collections.singletonList(pipeline))
                                       .adminInputs(Collections.emptyList())
                                       .build();
    when(repository.findByIdentifierAndVersion("workflow-1", "1.0.0")).thenReturn(entity);

    try (MockedStatic<NGRestUtils> ngRestUtilsMock = mockStatic(NGRestUtils.class)) {
      ngRestUtilsMock.when(() -> NGRestUtils.getGeneralResponse(any()))
          .thenThrow(new InvalidRequestException("Pipeline creation failed"));
      when(pipelineServiceClient.createPipeline(any(), eq(PIPELINE_ORG_ID), eq(PIPELINE_PROJECT_ID), eq(ACCOUNT_ID)))
          .thenReturn(mockCall);

      assertThrows(InvalidRequestException.class,
          ()
              -> service.install(ACCOUNT_ID, PIPELINE_ORG_ID, PIPELINE_PROJECT_ID, WORKFLOW_ORG_ID, WORKFLOW_PROJECT_ID,
                  "workflow-1", "1.0.0", INSTANCE_ID, null, new HashMap<>(), null, null));
    }
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testInstallFailsWhenWorkflowInstanceAlreadyExists() {
    WorkflowLibraryEntity entity = WorkflowLibraryEntity.builder()
                                       .identifier("workflow-1")
                                       .version("1.0.0")
                                       .workflowYaml("yaml")
                                       .pipelines(Collections.emptyList())
                                       .adminInputs(Collections.emptyList())
                                       .build();
    when(repository.findByIdentifierAndVersion("workflow-1", "1.0.0")).thenReturn(entity);
    doReturn(null)
        .when(catalogService)
        .getEntity(eq(ACCOUNT_ID), eq(WORKFLOW_ORG_ID), eq(WORKFLOW_PROJECT_ID),
            eq(CatalogUtils.entityRef("workflow", WORKFLOW_ORG_ID, WORKFLOW_PROJECT_ID, INSTANCE_ID)), eq(false),
            eq(false), eq(false));

    assertThrows(InvalidRequestException.class,
        ()
            -> service.install(ACCOUNT_ID, PIPELINE_ORG_ID, PIPELINE_PROJECT_ID, WORKFLOW_ORG_ID, WORKFLOW_PROJECT_ID,
                "workflow-1", "1.0.0", INSTANCE_ID, null, new HashMap<>(), null, null));
    verify(pipelineServiceClient, never()).createPipeline(any(), any(), any(), any());
  }

  private WorkflowLibraryEntity buildEntity(String identifier, String version) {
    return WorkflowLibraryEntity.builder()
        .identifier(identifier)
        .version(version)
        .name("Test Workflow")
        .category("DEPLOYMENT")
        .isStable(true)
        .deprecated(false)
        .workflowYaml("yaml")
        .pipelines(Collections.emptyList())
        .adminInputs(Collections.emptyList())
        .build();
  }
}
