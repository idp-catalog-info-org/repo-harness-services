/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import static io.harness.annotations.dev.HarnessTeam.IRO;
import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorServiceImpl;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.PagerDutyConnectorDTO;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.UnexpectedException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.service.DelegateGrpcClientWrapper;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(IRO)
@RunWith(MockitoJUnitRunner.class)
public class IRServiceImplTest extends CategoryTest {
  @Mock private ConnectorServiceImpl connectorService;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private SecretNGManagerClient secretNGManagerClient;
  @InjectMocks private IRServiceImpl irService;

  private static final String ACCOUNT_ID = "acc1";

  private MockedStatic<NGRestUtils> ngRestUtilsMock;

  @Before
  public void setUp() {
    ConnectorResponseDTO connectorResponse =
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder()
                           .identifier("pd")
                           .connectorConfig(
                               PagerDutyConnectorDTO.builder()
                                   .apiTokenRef(SecretRefData.builder().decryptedValue("tok".toCharArray()).build())
                                   .build())
                           .build())
            .build();
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponse));

    // getEncryptionDetails() routes through the static NGRestUtils.getResponse(...); stub it to an empty list.
    ngRestUtilsMock = mockStatic(NGRestUtils.class);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any())).thenReturn(Collections.emptyList());
  }

  @After
  public void tearDown() {
    ngRestUtilsMock.close();
  }

  private IrConnectorHttpRequest request() {
    return IrConnectorHttpRequest.builder()
        .method("GET")
        .url("https://api.example.com/x")
        .connectorIdentifier("account.pd")
        .delegateAccountId(ACCOUNT_ID)
        .build();
  }

  private HttpStepResponse httpResponse(int code, String body) {
    return HttpStepResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .httpResponseCode(code)
        .httpResponseBody(body)
        .build();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSuccessfulResponseParsed() {
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn((DelegateResponseData) httpResponse(200, "{\"name\":\"ok\"}"));

    HttpDelegateTaskResponse response = irService.connectorHttpTaskHandler(request());

    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getErrorMessage()).isNull();
    assertThat(response.getBody().get("name").asText()).isEqualTo("ok");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testRawBodyReturnedWithoutExtraction() {
    // ng-manager is a thin pass-through: the full body is returned as-is, no JMESPath/output selection.
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn((DelegateResponseData) httpResponse(200, "{\"result\":{\"number\":\"INC001\"}}"));

    HttpDelegateTaskResponse response = irService.connectorHttpTaskHandler(request());

    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getBody().get("result").get("number").asText()).isEqualTo("INC001");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testNon2xxReturnedVerbatim() {
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn((DelegateResponseData) httpResponse(401, "{\"message\":\"unauthorized\"}"));

    HttpDelegateTaskResponse response = irService.connectorHttpTaskHandler(request());

    // The target's status code and raw body are passed through unchanged; ng-manager does not synthesize an error
    // message or alter the body. The caller (ai-sre) decides how to handle non-2xx.
    assertThat(response.getStatusCode()).isEqualTo(401);
    assertThat(response.getErrorMessage()).isNull();
    assertThat(response.getBody().get("message").asText()).isEqualTo("unauthorized");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testUnexpectedDelegateResponseThrows() {
    // Delegate returns a non-HttpStepResponse (e.g. error response) -> server-side fault.
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(new DelegateResponseData() {});

    assertThatThrownBy(() -> irService.connectorHttpTaskHandler(request()))
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContaining("Unexpected response from delegate");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testConnectorNotFoundThrows() {
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> irService.connectorHttpTaskHandler(request())).isInstanceOf(UnexpectedException.class);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testNullRequestThrows() {
    assertThatThrownBy(() -> irService.connectorHttpTaskHandler(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testOrgScopedConnectorResolvedAtConnectorScopeNotDelegateScope() {
    // Org-scoped connector executed by an account-level delegate: connector resolves at its own org scope,
    // while the task is owned at account scope (no delegateOrgId).
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn((DelegateResponseData) httpResponse(200, "{}"));
    ArgumentCaptor<String> orgCaptor = ArgumentCaptor.forClass(String.class);

    IrConnectorHttpRequest request = IrConnectorHttpRequest.builder()
                                         .method("GET")
                                         .url("https://api.example.com/x")
                                         .connectorIdentifier("org.snow")
                                         .delegateAccountId(ACCOUNT_ID)
                                         .connectorOrgId("myOrg")
                                         .build();

    irService.connectorHttpTaskHandler(request);

    // connectorService.get(account, org, project, identifier): org-scoped connector resolves with connectorOrgId
    // and a null project, independent of the (account-only) delegate scope.
    verify(connectorService).get(eq(ACCOUNT_ID), orgCaptor.capture(), eq(null), eq("snow"));
    assertThat(orgCaptor.getValue()).isEqualTo("myOrg");
  }
}
