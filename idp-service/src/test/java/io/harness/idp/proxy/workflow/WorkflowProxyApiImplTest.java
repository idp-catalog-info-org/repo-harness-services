/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.workflow;

import static io.harness.rule.OwnerRule.HARJAS;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.clients.BackstageResourceClient;
import io.harness.clients.BackstageScaffolderTaskRequest;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.proxy.workflow.resource.WorkflowProxyApiImpl;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.WorkflowExecutionRequest;

import java.io.IOException;
import java.util.HashMap;
import javax.ws.rs.core.Response;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;

@OwnedBy(HarnessTeam.IDP)
public class WorkflowProxyApiImplTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "TEST_ACCOUNT_IDENTIFIER";
  private static final String ORG_IDENTIFIER = "TEST_ORG_IDENTIFIER";
  private static final String PROJECT_IDENTIFIER = "TEST_PROJECT_IDENTIFIER";
  private static final String IDENTIFIER = "test-identifier";
  private Call<Object> call;
  AutoCloseable openMocks;

  @Mock private BackstageResourceClient backstageResourceClient;
  @Mock private CatalogServiceHelper catalogServiceHelper;

  @InjectMocks private WorkflowProxyApiImpl workflowProxyApi;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    call = mock(Call.class);
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testExecuteWorkflowV2_Success() throws IOException {
    // Arrange
    WorkflowExecutionRequest request = new WorkflowExecutionRequest();
    request.setIdentifier(IDENTIFIER);
    HashMap<String, String> values = new HashMap<>();
    values.put("key", "value");
    request.setValues(values);
    HashMap<String, String> secrets = new HashMap<>();
    secrets.put("secretKey", "secretValue");
    request.setSecrets(secrets);

    String workflowRef = CatalogUtils.entityRef("workflow", ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER);

    retrofit2.Response<Object> response = retrofit2.Response.success("Success");
    when(call.execute()).thenReturn(response);

    // Use argument captor to verify the BackstageScaffolderTaskRequest
    ArgumentCaptor<BackstageScaffolderTaskRequest> requestCaptor =
        ArgumentCaptor.forClass(BackstageScaffolderTaskRequest.class);
    when(backstageResourceClient.executeScaffolderTask(eq(ACCOUNT_IDENTIFIER), requestCaptor.capture()))
        .thenReturn(call);

    // Act
    Response actualResponse =
        workflowProxyApi.executeWorkflowV2(request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    // Assert
    assertEquals(201, actualResponse.getStatus());
    verify(catalogServiceHelper)
        .checkCrudRbac(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "workflow", workflowRef, "execute");

    // Verify the captured BackstageScaffolderTaskRequest
    BackstageScaffolderTaskRequest capturedRequest = requestCaptor.getValue();
    assertEquals(workflowRef.replace("workflow:", ""), capturedRequest.getTemplateRef().replace("template:", ""));
    assertEquals(values, capturedRequest.getValues());
    assertEquals(secrets, capturedRequest.getSecrets());
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testExecuteWorkflowV2_MissingIdentifier() {
    // Arrange
    WorkflowExecutionRequest request = new WorkflowExecutionRequest();
    // Identifier is null

    // Act
    Response actualResponse =
        workflowProxyApi.executeWorkflowV2(request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    // Assert
    assertEquals(500, actualResponse.getStatus());

    // No call should be made to backstageResourceClient
    Mockito.verifyNoInteractions(backstageResourceClient);
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testExecuteWorkflowV2_EmptyIdentifier() {
    // Arrange
    WorkflowExecutionRequest request = new WorkflowExecutionRequest();
    request.setIdentifier("");

    // Act
    Response actualResponse =
        workflowProxyApi.executeWorkflowV2(request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    // Assert
    assertEquals(500, actualResponse.getStatus());

    // No call should be made to backstageResourceClient
    Mockito.verifyNoInteractions(backstageResourceClient);
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testExecuteWorkflowV2_BackendError() throws IOException {
    // Arrange
    WorkflowExecutionRequest request = new WorkflowExecutionRequest();
    request.setIdentifier(IDENTIFIER);

    String workflowRef = CatalogUtils.entityRef("workflow", ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER);

    retrofit2.Response<Object> response =
        retrofit2.Response.error(500, ResponseBody.create(MediaType.parse("application/json"), "Error"));
    when(call.execute()).thenReturn(response);

    // Use argument captor to verify the BackstageScaffolderTaskRequest
    ArgumentCaptor<BackstageScaffolderTaskRequest> requestCaptor =
        ArgumentCaptor.forClass(BackstageScaffolderTaskRequest.class);
    when(backstageResourceClient.executeScaffolderTask(eq(ACCOUNT_IDENTIFIER), requestCaptor.capture()))
        .thenReturn(call);

    // Act
    Response actualResponse =
        workflowProxyApi.executeWorkflowV2(request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    // Assert
    assertEquals(500, actualResponse.getStatus());
    verify(catalogServiceHelper)
        .checkCrudRbac(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "workflow", workflowRef, "execute");

    // Verify the captured BackstageScaffolderTaskRequest
    BackstageScaffolderTaskRequest capturedRequest = requestCaptor.getValue();
    assertEquals(workflowRef.replace("workflow:", ""), capturedRequest.getTemplateRef().replace("template:", ""));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testExecuteWorkflowV2_Exception() {
    // Arrange
    WorkflowExecutionRequest request = new WorkflowExecutionRequest();
    request.setIdentifier(IDENTIFIER);

    String workflowRef = CatalogUtils.entityRef("workflow", ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER);

    // Use argument captor to verify the BackstageScaffolderTaskRequest
    ArgumentCaptor<BackstageScaffolderTaskRequest> requestCaptor =
        ArgumentCaptor.forClass(BackstageScaffolderTaskRequest.class);
    RuntimeException exception = new RuntimeException("Test exception");
    when(backstageResourceClient.executeScaffolderTask(eq(ACCOUNT_IDENTIFIER), requestCaptor.capture()))
        .thenThrow(exception);

    // Act
    Response actualResponse =
        workflowProxyApi.executeWorkflowV2(request, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    // Assert
    assertEquals(500, actualResponse.getStatus());
    verify(catalogServiceHelper)
        .checkCrudRbac(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "workflow", workflowRef, "execute");

    // Verify the captured BackstageScaffolderTaskRequest
    BackstageScaffolderTaskRequest capturedRequest = requestCaptor.getValue();
    assertEquals(workflowRef.replace("workflow:", ""), capturedRequest.getTemplateRef().replace("template:", ""));
  }
}
