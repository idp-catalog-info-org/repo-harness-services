/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepOutputV2;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.exception.InvalidRequestException;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.product.ci.engine.proto.OutputVariable;
import io.harness.rule.Owner;
import io.harness.servicenow.ServiceNowFieldValueNG;
import io.harness.servicenow.ServiceNowTicketNG;
import io.harness.tasks.ResponseData;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDC)
public class UnifiedApprovalUtilsTest extends CategoryTest {
  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;
  private ObjectMapper objectMapper;

  @InjectMocks private UnifiedApprovalUtils utils;

  @Before
  public void setUp() {
    objectMapper = new ObjectMapper();
    utils = new UnifiedApprovalUtils(serializedResponseDataHelper, objectMapper);
  }

  private static final String ACCOUNT_ID = "accountId";

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void getOutputVariables_shouldEncodeSecretWithToken() {
    StepOutputV2 secretVar = StepOutputV2.builder().key("SecretKey").value("s3cr3t").type("SECRET").build();
    StepOutputV2 plainVar = StepOutputV2.builder().key("PlainKey").value("plain").type("STRING").build();

    List<StepOutputV2> outputs = new ArrayList<>();
    outputs.add(secretVar);
    outputs.add(plainVar);

    Map<String, String> defaults = Collections.singletonMap("DefaultOutVar", "DefaultOutVarValue");

    Map<String, String> result = utils.getOutputVariables(outputs, defaults, ACCOUNT_ID);

    String secret = result.get("SecretKey");
    assertThat(secret).startsWith(UnifiedApprovalUtils.SWEEPING_OUTPUT_SECRET_OBTAIN_PREFIX);
    assertThat(secret).contains("SecretKey");

    assertThat(result).containsKey("SecretKey").containsKey("PlainKey");
    assertThat(result.get("PlainKey")).isEqualTo("plain");
    // DEFAULT should not be used when outputs present
    assertThat(result).doesNotContainKey("DefaultOutVar");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void getOutputVariables_shouldReturnDefaultsWhenNoOutputs() {
    Map<String, String> defaults = new HashMap<>();
    defaults.put("OutVar", "OutVarValue");
    defaults.put("OutVar1", "OutVarValue1");

    Map<String, String> result = utils.getOutputVariables(Collections.emptyList(), defaults, ACCOUNT_ID);

    assertThat(result).containsExactlyInAnyOrderEntriesOf(defaults);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void handleUnifiedApprovalResponse_shouldHandleVmTaskExecutionResponse() {
    ResponseData input = mock(ResponseData.class);
    VmTaskExecutionResponse vmResp = mock(VmTaskExecutionResponse.class);

    // deserialize to VmTaskExecutionResponse
    when(serializedResponseDataHelper.deserialize(input)).thenReturn(vmResp);

    // Prepare outputs
    OutputVariable plainVar = OutputVariable.newBuilder()
                                  .setKey("OutVar")
                                  .setValue("OutVarValue")
                                  .setType(OutputVariable.OutputType.STRING)
                                  .build();

    StepOutputV2 stepOut = mock(StepOutputV2.class);
    when(stepOut.getKey()).thenReturn(plainVar.getKey());
    when(stepOut.getType()).thenReturn(plainVar.getType().toString());
    when(stepOut.getValue()).thenReturn(plainVar.getValue());
    when(vmResp.getOutputs()).thenReturn(Collections.singletonList(stepOut));

    Map<String, String> outputVars = new HashMap<>();
    outputVars.put("DefaultOutVar", "DefaultOutVarValue");
    when(vmResp.getOutputVars()).thenReturn(outputVars);

    Map<String, String> result = utils.handleUnifiedApprovalResponse(input, ACCOUNT_ID);

    assertThat(result).containsEntry("OutVar", "OutVarValue");
    assertThat(result).doesNotContainKey("DefaultOutVar");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void handleUnifiedApprovalResponse_shouldHandleStepStatusTaskResponseData() {
    ResponseData input = mock(ResponseData.class);
    StepStatusTaskResponseData stepStatusResp = mock(StepStatusTaskResponseData.class);
    when(serializedResponseDataHelper.deserialize(input)).thenReturn(stepStatusResp);

    io.harness.delegate.task.stepstatus.StepStatus status =
        io.harness.delegate.task.stepstatus.StepStatus.builder().build();
    when(stepStatusResp.getStepStatus()).thenReturn(status);

    status.setOutputV2(List.of(StepOutputV2.builder().key("OutVar").value("OutVarValue").type("STRING").build()));
    status.setOutput(StepMapOutput.builder().map(Map.of("DefaultOutVar", "DefaultOutVarValue")).build());

    Map<String, String> result = utils.handleUnifiedApprovalResponse(input, ACCOUNT_ID);

    assertThat(result).containsEntry("OutVar", "OutVarValue");
    assertThat(result).doesNotContainKey("DefaultOutVar");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void handleUnifiedApprovalResponse_shouldReturnEmptyMapForUnknownResponse() {
    ResponseData input = mock(ResponseData.class);
    when(serializedResponseDataHelper.deserialize(input)).thenReturn(mock(ResponseData.class));

    Map<String, String> result = utils.handleUnifiedApprovalResponse(input, ACCOUNT_ID);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void deserializeOutputVariable_shouldReturnNullForEmptyJson() {
    String empty = null;
    String blank = "";

    assertThat(utils.deserializeOutputVariable(empty, Map.class)).isNull();
    assertThat(utils.deserializeOutputVariable(blank, Map.class)).isNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void deserializeOutputVariable_shouldThrowWhenTypeIsNull() {
    assertThatThrownBy(() -> utils.deserializeOutputVariable("{}", null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Variable type cannot be null for deserialization");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void deserializeOutputVariable_shouldReturnParsedValue() throws Exception {
    String json = "{\n"
        + "  \"url\": "
        + "\"https://example.service-now.com/nav_to.do?uri=incident.do?sys_id=9f2b1c3d4e5f678901234567890abcde\",\n"
        + "  \"number\": \"INC0012345\",\n"
        + "  \"fields\": {\n"
        + "    \"short_description\": {\n"
        + "      \"value\": \"User cannot access VPN\",\n"
        + "      \"displayValue\": \"User cannot access VPN\"\n"
        + "    },\n"
        + "    \"description\": {\n"
        + "      \"value\": \"User reports VPN client fails with error 809 when connecting from home.\",\n"
        + "      \"displayValue\": \"User reports VPN client fails with error 809 when connecting from home.\"\n"
        + "    }\n"
        + "  }\n"
        + "}";

    @SuppressWarnings("unchecked") Class<ServiceNowTicketNG> type = ServiceNowTicketNG.class;

    // Build parsed object to be returned by the mocked objectMapper
    ServiceNowFieldValueNG shortDesc =
        ServiceNowFieldValueNG.builder().value("User cannot access VPN").displayValue("User cannot access VPN").build();

    ServiceNowFieldValueNG description =
        ServiceNowFieldValueNG.builder()
            .value("User reports VPN client fails with error 809 when connecting from home.")
            .displayValue("User reports VPN client fails with error 809 when connecting from home.")
            .build();

    Map<String, ServiceNowFieldValueNG> fields = new HashMap<>();
    fields.put("short_description", shortDesc);
    fields.put("description", description);

    ServiceNowTicketNG parsed =
        ServiceNowTicketNG.builder()
            .url("https://example.service-now.com/nav_to.do?uri=incident.do?sys_id=9f2b1c3d4e5f678901234567890abcde")
            .number("INC0012345")
            .fields(fields)
            .build();

    // Act
    ServiceNowTicketNG result = utils.deserializeOutputVariable(json, type);

    // Assert top-level fields
    assertThat(result).isNotNull();
    assertThat(result.getUrl())
        .isEqualTo("https://example.service-now.com/nav_to.do?uri=incident.do?sys_id=9f2b1c3d4e5f678901234567890abcde");
    assertThat(result.getNumber()).isEqualTo("INC0012345");

    // Assert fields map
    assertThat(result.getFields()).isNotNull();
    assertThat(result.getFields()).hasSize(2);
    assertThat(result.getFields()).containsKeys("short_description", "description");

    // Assert nested field: short_description
    ServiceNowFieldValueNG sd = result.getFields().get("short_description");
    assertThat(sd).isNotNull();
    assertThat(sd.getValue()).isEqualTo("User cannot access VPN");
    assertThat(sd.getDisplayValue()).isEqualTo("User cannot access VPN");

    // Assert nested field: description
    ServiceNowFieldValueNG desc = result.getFields().get("description");
    assertThat(desc).isNotNull();
    assertThat(desc.getValue()).isEqualTo("User reports VPN client fails with error 809 when connecting from home.");
    assertThat(desc.getDisplayValue())
        .isEqualTo("User reports VPN client fails with error 809 when connecting from home.");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void deserializeOutputVariable_shouldWrapJsonError() throws Exception {
    String json = "{ invalid }";

    assertThatThrownBy(() -> utils.deserializeOutputVariable(json, Map.class))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Failed to deserialize output variable");
  }
}
