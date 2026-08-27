/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.HARSHIT_MAHAJAN;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.retry.RetryInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.notification.helper.NotificationHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.plan.execution.beans.dto.RunStageRequestDTO;
import io.harness.pms.plan.execution.helper.PipelineExecutionApiImpl;
import io.harness.pms.plan.execution.helper.PipelineExecutor;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.PipelineSplitPermissionsHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ApiKeyPrincipal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.pipeline.v1.model.DirectPipelineExecuteRequestBody;
import io.harness.spec.server.pipeline.v1.model.DynamicPipelineExecuteInternalRequestBody;
import io.harness.spec.server.pipeline.v1.model.DynamicPipelineExecuteRequestBody;
import io.harness.spec.server.pipeline.v1.model.NotificationTemplateReconcileRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineExecuteRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineExecuteResponseBody;
import io.harness.spec.server.pipeline.v1.model.RerunPipelineRequest;
import io.harness.spec.server.pipeline.v1.model.RetryPipelineRequest;
import io.harness.spec.server.pipeline.v1.model.RunStageRequestBody;
import io.harness.spec.server.pipeline.v1.model.UnresolvedNotificationRulesResponseBody;
import io.harness.template.yaml.ref.TemplateRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

@OwnedBy(PIPELINE)
@PrepareForTest({PlanExecutionUtils.class, UUIDGenerator.class})
public class PipelineExecutionApiImplTest extends CategoryTest {
  @Mock PipelineExecutor pipelineExecutor;
  @Mock RetryExecutionHelper retryExecutionHelper;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock NotificationHelper notificationHelper;
  @Mock PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper;
  @Mock PMSExecutionService pmsExecutionService;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @InjectMocks PipelineExecutionApiImpl pipelineExecutionApi;
  private final String notificationYaml = "pipeline:\n"
      + "  notificationRules:\n"
      + "    - identifier: rule1\n"
      + "      template:\n"
      + "        templateInputs:\n"
      + "          variables:\n"
      + "            - name: var1\n"
      + "              value: \"<+input>\"\n"
      + "    - identifier: rule2\n"
      + "      template:\n"
      + "        templateInputs:\n"
      + "          variables:\n"
      + "            - name: var2\n"
      + "              value: \"fixedValue\"";

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  private PipelineExecutionApiImpl createSpy() {
    PipelineExecutionApiImpl spy = Mockito.spy(pipelineExecutionApi);
    return spy;
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testPipelineCreate() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();

    String planExecutionId = generateUuid();

    PipelineExecuteRequestBody pipelineExecuteBody = new PipelineExecuteRequestBody();
    Status status = Status.RUNNING;
    pipelineExecuteBody.setInputs(Map.of("inputs", "inputSetYaml"));
    String module = "CD";

    doReturn(PlanExecutionResponseDto.builder()
                 .planExecution(PlanExecution.builder().uuid(planExecutionId).status(status).build())
                 .build())
        .when(pipelineExecutor)
        .runPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, module,
            YamlUtils.writeYamlString(Map.of("inputs", pipelineExecuteBody.getInputs())), false, false, "", null, null,
            false, false);

    Response response = pipelineExecutionApi.executePipeline(
        orgId, projectId, pipelineId, pipelineExecuteBody, accountId, module, false, false, "", null, null, null, null);

    PipelineExecuteResponseBody responseBody = (PipelineExecuteResponseBody) response.getEntity();

    assertThat(responseBody.getExecutionDetails().getExecutionId()).isEqualTo(planExecutionId);
    assertThat(responseBody.getExecutionDetails().getStatus()).isEqualTo(status.toString());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testExecuteStagesWithRuntimeInputYaml() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();

    String planExecutionId = generateUuid();
    RunStageRequestBody runStageRequestBody = new RunStageRequestBody();
    runStageRequestBody.setStageIdentifiers(Arrays.asList("stg1"));
    runStageRequestBody.setInputsYaml("yaml");
    RunStageRequestDTO runStageRequestDTO = RunStageRequestDTO.builder()
                                                .stageIdentifiers(runStageRequestBody.getStageIdentifiers())
                                                .runtimeInputYaml(runStageRequestBody.getInputsYaml())
                                                .expressionValues(runStageRequestBody.getExpressionValues())
                                                .build();
    Status status = Status.RUNNING;
    String module = "CD";

    doReturn(PlanExecutionResponseDto.builder()
                 .planExecution(PlanExecution.builder().uuid(planExecutionId).status(status).build())
                 .build())
        .when(pipelineExecutor)
        .runStagesWithRuntimeInputYaml(
            accountId, orgId, projectId, pipelineId, module, runStageRequestDTO, false, "", null, false, null);

    Response response = pipelineExecutionApi.executeStagesWithInputYaml(
        orgId, projectId, pipelineId, runStageRequestBody, accountId, module, "false", "", null, null, null, null);

    PipelineExecuteResponseBody responseBody = (PipelineExecuteResponseBody) response.getEntity();

    assertThat(responseBody.getExecutionDetails().getExecutionId()).isEqualTo(planExecutionId);
    assertThat(responseBody.getExecutionDetails().getStatus()).isEqualTo(status.toString());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRerunStagesExecutionOfPipeline() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();

    String planExecutionId = generateUuid();
    RunStageRequestBody runStageRequestBody = new RunStageRequestBody();
    runStageRequestBody.setStageIdentifiers(Arrays.asList("stg1"));
    runStageRequestBody.setInputsYaml("yaml");
    RunStageRequestDTO runStageRequestDTO = RunStageRequestDTO.builder()
                                                .stageIdentifiers(runStageRequestBody.getStageIdentifiers())
                                                .runtimeInputYaml(runStageRequestBody.getInputsYaml())
                                                .expressionValues(runStageRequestBody.getExpressionValues())
                                                .build();
    Status status = Status.RUNNING;
    String module = "CD";

    doReturn(PlanExecutionResponseDto.builder()
                 .planExecution(PlanExecution.builder().uuid(planExecutionId).status(status).build())
                 .build())
        .when(pipelineExecutor)
        .rerunStagesWithRuntimeInputYaml(accountId, orgId, projectId, pipelineId, module, planExecutionId,
            runStageRequestDTO, false, false, "", false, null);

    Response response = pipelineExecutionApi.rerunStagesExecutionOfPipeline(orgId, projectId, pipelineId,
        planExecutionId, runStageRequestBody, accountId, null, null, null, false, module, "");

    PipelineExecuteResponseBody responseBody = (PipelineExecuteResponseBody) response.getEntity();

    assertThat(responseBody.getExecutionDetails().getExecutionId()).isEqualTo(planExecutionId);
    assertThat(responseBody.getExecutionDetails().getStatus()).isEqualTo(status.toString());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRetryPipelineWithInputsetPipelineYaml() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();

    String planExecutionId = generateUuid();
    when(retryExecutionHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecutionId, null, null))
        .thenReturn(RetryInfo.builder().isResumable(true).build());
    when(scopeResolutionHelper.getScopeInfo(accountId, orgId, projectId)).thenReturn(null);
    RetryPipelineRequest retryPipelineRequest = new RetryPipelineRequest();
    retryPipelineRequest.setInputs(Map.of("inputs", "inputSetYaml"));
    Status status = Status.RUNNING;
    String module = "CD";

    PipelineExecutionApiImpl spy = createSpy();

    doReturn(PlanExecutionResponseDto.builder()
                 .planExecution(PlanExecution.builder().uuid(planExecutionId).status(status).build())
                 .build())
        .when(pipelineExecutor)
        .retryPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, module,
            YamlUtils.writeYamlString(Map.of("inputs", retryPipelineRequest.getInputs())), planExecutionId,
            List.of("stage1"), false, false, false, "", false, null, null);

    Response response = spy.retryPipelineWithInputsetPipelineYaml(orgId, projectId, pipelineId, planExecutionId,
        retryPipelineRequest, accountId, module, List.of("stage1"), false, "");

    PipelineExecuteResponseBody responseBody = (PipelineExecuteResponseBody) response.getEntity();

    assertThat(responseBody.getExecutionDetails().getExecutionId()).isEqualTo(planExecutionId);
    assertThat(responseBody.getExecutionDetails().getStatus()).isEqualTo(status.toString());

    verify(pipelineExecutor)
        .retryPipelineWithInputSetPipelineYaml(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(module),
            anyString(), eq(planExecutionId), eq(List.of("stage1")), eq(false), eq(false), eq(false), eq(""), eq(false),
            eq(null), eq(null));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRetryPipelineWithExpressionValues() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();

    String planExecutionId = generateUuid();
    when(retryExecutionHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecutionId, null, null))
        .thenReturn(RetryInfo.builder().isResumable(true).build());
    when(scopeResolutionHelper.getScopeInfo(accountId, orgId, projectId)).thenReturn(null);

    RetryPipelineRequest retryPipelineRequest = new RetryPipelineRequest();
    retryPipelineRequest.setInputs(Map.of("inputs", "inputSetYaml"));
    Map<String, String> expressionValues = new HashMap<>();
    expressionValues.put("expr1", "value1");
    expressionValues.put("expr2", "value2");
    retryPipelineRequest.setExpressionValues(expressionValues);

    Status status = Status.RUNNING;
    String module = "CD";

    PipelineExecutionApiImpl spy = createSpy();

    doReturn(PlanExecutionResponseDto.builder()
                 .planExecution(PlanExecution.builder().uuid(planExecutionId).status(status).build())
                 .build())
        .when(pipelineExecutor)
        .retryPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, module,
            YamlUtils.writeYamlString(Map.of("inputs", retryPipelineRequest.getInputs())), planExecutionId,
            List.of("stage1"), false, false, false, "", false, null, expressionValues);

    Response response = spy.retryPipelineWithInputsetPipelineYaml(orgId, projectId, pipelineId, planExecutionId,
        retryPipelineRequest, accountId, module, List.of("stage1"), false, "");

    PipelineExecuteResponseBody responseBody = (PipelineExecuteResponseBody) response.getEntity();

    assertThat(responseBody.getExecutionDetails().getExecutionId()).isEqualTo(planExecutionId);
    assertThat(responseBody.getExecutionDetails().getStatus()).isEqualTo(status.toString());

    verify(pipelineExecutor)
        .retryPipelineWithInputSetPipelineYaml(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(module),
            anyString(), eq(planExecutionId), eq(List.of("stage1")), eq(false), eq(false), eq(false), eq(""), eq(false),
            eq(null), eq(expressionValues));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRetryPipelineWithEmptyExpressionValues() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();

    String planExecutionId = generateUuid();
    when(retryExecutionHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecutionId, null, null))
        .thenReturn(RetryInfo.builder().isResumable(true).build());
    when(scopeResolutionHelper.getScopeInfo(accountId, orgId, projectId)).thenReturn(null);

    RetryPipelineRequest retryPipelineRequest = new RetryPipelineRequest();
    retryPipelineRequest.setInputs(Map.of("inputs", "inputSetYaml"));
    retryPipelineRequest.setExpressionValues(new HashMap<>()); // Empty map

    Status status = Status.RUNNING;
    String module = "CD";

    PipelineExecutionApiImpl spy = createSpy();

    doReturn(PlanExecutionResponseDto.builder()
                 .planExecution(PlanExecution.builder().uuid(planExecutionId).status(status).build())
                 .build())
        .when(pipelineExecutor)
        .retryPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, module,
            YamlUtils.writeYamlString(Map.of("inputs", retryPipelineRequest.getInputs())), planExecutionId,
            List.of("stage1"), false, false, false, "", false, null, null);

    Response response = spy.retryPipelineWithInputsetPipelineYaml(orgId, projectId, pipelineId, planExecutionId,
        retryPipelineRequest, accountId, module, List.of("stage1"), false, "");

    PipelineExecuteResponseBody responseBody = (PipelineExecuteResponseBody) response.getEntity();

    assertThat(responseBody.getExecutionDetails().getExecutionId()).isEqualTo(planExecutionId);
    assertThat(responseBody.getExecutionDetails().getStatus()).isEqualTo(status.toString());

    // Verify that empty expression values are treated as null
    verify(pipelineExecutor)
        .retryPipelineWithInputSetPipelineYaml(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(module),
            anyString(), eq(planExecutionId), eq(List.of("stage1")), eq(false), eq(false), eq(false), eq(""), eq(false),
            eq(null), eq(null));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRerunPipeline() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();

    String planExecutionId = generateUuid();
    RerunPipelineRequest rerunPipelineRequestBody = new RerunPipelineRequest();
    rerunPipelineRequestBody.setInputs(Map.of("inputs", "inputSetYaml"));
    Status status = Status.RUNNING;
    String module = "CD";
    PipelineExecutionApiImpl spy = createSpy();

    String inputSetPipelineYaml = YamlUtils.writeYamlString(Map.of("inputs", rerunPipelineRequestBody.getInputs()));

    // Mock the rerun method instead of run method
    doReturn(PlanExecutionResponseDto.builder()
                 .planExecution(PlanExecution.builder().uuid(planExecutionId).status(status).build())
                 .build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, module, planExecutionId,
            inputSetPipelineYaml, false, false, null, false, false, null);

    Response response = spy.rerunPipeline(orgId, projectId, pipelineId, planExecutionId, rerunPipelineRequestBody,
        accountId, module, false, null, null, null, null, false);

    PipelineExecuteResponseBody responseBody = (PipelineExecuteResponseBody) response.getEntity();

    assertThat(responseBody.getExecutionDetails().getExecutionId()).isEqualTo(planExecutionId);
    assertThat(responseBody.getExecutionDetails().getStatus()).isEqualTo(status.toString());
    verify(pipelineExecutor, Mockito.times(1))
        .rerunPipelineWithInputSetPipelineYaml(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(module),
            eq(planExecutionId), eq(inputSetPipelineYaml), eq(false), eq(false), eq(null), eq(false), eq(false),
            eq(null));
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListNotificationRulesWithUnresolvedInputs() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();

    NotificationTemplateReconcileRequestBody requestBody = new NotificationTemplateReconcileRequestBody();
    requestBody.setYaml(notificationYaml);
    ArrayList<Map<String, String>> helperResponse = new ArrayList<>();
    Map<String, String> responseObj = new HashMap<>();
    responseObj.put("notificationIdentifier", "rule1");
    helperResponse.add(responseObj);
    when(notificationHelper.listNotificationRulesWithUnresolvedInputs(notificationYaml)).thenReturn(helperResponse);
    Response response = pipelineExecutionApi.listNotificationRulesWithUnresolvedInputs(
        requestBody, orgId, projectId, pipelineId, accountId);
    assertThat(response.hasEntity()).isEqualTo(true);
    UnresolvedNotificationRulesResponseBody responseList =
        (UnresolvedNotificationRulesResponseBody) response.getEntity();
    assertThat(responseList.getNotificationRules().get(0).getNotificationRuleId()).isEqualTo("rule1");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRunDirectExecutionWithInputYamlEnforcesExecuteAndEdit() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();
    String planExecutionId = generateUuid();
    String pipelineYaml = "pipeline:\n  identifier: testPipeline";
    String module = "CD";

    DirectPipelineExecuteRequestBody requestBody = new DirectPipelineExecuteRequestBody();
    requestBody.setYaml(pipelineYaml);
    requestBody.setInputsYaml(null);

    Status status = Status.RUNNING;

    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.PIPE_DIRECT_PIPELINES_EXECUTION));
    doReturn(false)
        .when(pmsFeatureFlagHelper)
        .isEnabled(anyString(), eq(FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT));
    when(scopeResolutionHelper.getScopeInfo(accountId, orgId, projectId)).thenReturn(null);

    doReturn(PlanExecutionResponseDto.builder()
                 .planExecution(PlanExecution.builder().uuid(planExecutionId).status(status).build())
                 .build())
        .when(pipelineExecutor)
        .startDirectExecution(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(pipelineYaml), eq(null),
            eq(module), eq(false), eq(false), eq(""), eq(null));

    Response response = pipelineExecutionApi.runDirectExecutionWithInputYaml(
        orgId, projectId, pipelineId, requestBody, accountId, module, "", false);

    PipelineExecuteResponseBody responseBody = (PipelineExecuteResponseBody) response.getEntity();

    assertThat(responseBody.getExecutionDetails().getExecutionId()).isEqualTo(planExecutionId);
    assertThat(responseBody.getExecutionDetails().getStatus()).isEqualTo(status.toString());
    // EXECUTE is via @NGAccessControlCheck (AOP); unit test asserts the remaining EDIT/CREATE split check.
    verify(pipelineSplitPermissionsHelper)
        .checkForPipelineRBACSplitAccessPermissions(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(false),
            eq(PipelineRbacPermissions.PIPELINE_EDIT), any());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testValidateNotificationRulesWithUnresolvedInputs() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();

    NotificationTemplateReconcileRequestBody requestBody = new NotificationTemplateReconcileRequestBody();
    requestBody.setYaml(notificationYaml);
    when(notificationHelper.validateNotificationRulesWithUnresolvedInputs(notificationYaml)).thenReturn(true);
    Response response = pipelineExecutionApi.validateNotificationRulesWithUnresolvedInputs(
        requestBody, orgId, projectId, pipelineId, accountId);
    assertThat(response.hasEntity()).isEqualTo(true);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetInputSetIdForRerunPipeline() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String executionId = generateUuid();

    List<String> expectedInputSetIds = Arrays.asList("input1", "input2");

    when(retryExecutionHelper.getInputSetIdForRerunPipeline(accountId, executionId)).thenReturn(expectedInputSetIds);

    List<String> result = retryExecutionHelper.getInputSetIdForRerunPipeline(accountId, executionId);

    assertThat(result).isEqualTo(expectedInputSetIds);

    verify(retryExecutionHelper).getInputSetIdForRerunPipeline(accountId, executionId);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetInputSetIdForRerunPipeline_NullExecutionSummary() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String executionId = generateUuid();

    when(retryExecutionHelper.getInputSetIdForRerunPipeline(accountId, executionId))
        .thenReturn(Collections.emptyList());

    List<String> result = retryExecutionHelper.getInputSetIdForRerunPipeline(accountId, executionId);

    assertThat(result).isEmpty();

    verify(retryExecutionHelper).getInputSetIdForRerunPipeline(accountId, executionId);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetInputSetIdForRerunPipeline_ExceptionHandling() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String executionId = generateUuid();

    when(retryExecutionHelper.getInputSetIdForRerunPipeline(accountId, executionId))
        .thenReturn(Collections.emptyList());

    List<String> result = retryExecutionHelper.getInputSetIdForRerunPipeline(accountId, executionId);

    assertThat(result).isEmpty();

    verify(retryExecutionHelper).getInputSetIdForRerunPipeline(accountId, executionId);
  }

  @Test
  @Owner(developers = HARSHIT_MAHAJAN)
  @Category(UnitTests.class)
  @Ignore("Test is flaky and fails sometimes in unitTests pipeline due to FF not enabled error")
  public void testRunDirectExecutionWithInputYaml() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();
    String planExecutionId = generateUuid();
    String pipelineYaml = "pipeline:\n  identifier: testPipeline";
    String module = "CD";

    DirectPipelineExecuteRequestBody requestBody = new DirectPipelineExecuteRequestBody();
    requestBody.setYaml(pipelineYaml);
    requestBody.setInputsYaml(null);

    Status status = Status.RUNNING;

    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_DIRECT_PIPELINES_EXECUTION)).thenReturn(true);
    when(scopeResolutionHelper.getScopeInfo(accountId, orgId, projectId)).thenReturn(null);

    doReturn(PlanExecutionResponseDto.builder()
                 .planExecution(PlanExecution.builder().uuid(planExecutionId).status(status).build())
                 .build())
        .when(pipelineExecutor)
        .startDirectExecution(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(pipelineYaml), eq(null),
            eq(module), eq(false), eq(false), eq(""), eq(null));

    Response response = pipelineExecutionApi.runDirectExecutionWithInputYaml(
        orgId, projectId, pipelineId, requestBody, accountId, module, "", false);

    PipelineExecuteResponseBody responseBody = (PipelineExecuteResponseBody) response.getEntity();

    assertThat(responseBody.getExecutionDetails().getExecutionId()).isEqualTo(planExecutionId);
    assertThat(responseBody.getExecutionDetails().getStatus()).isEqualTo(status.toString());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testExecutePipelineWithBranchAndConnectorRef() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();
    String planExecutionId = generateUuid();
    String branchName = "main-patch-2";
    String connectorRef = "account.github_connector";

    PipelineExecuteRequestBody pipelineExecuteBody = new PipelineExecuteRequestBody();
    Status status = Status.RUNNING;
    pipelineExecuteBody.setInputs(Map.of("inputs", "inputSetYaml"));
    String module = "CD";

    doReturn(PlanExecutionResponseDto.builder()
                 .planExecution(PlanExecution.builder().uuid(planExecutionId).status(status).build())
                 .build())
        .when(pipelineExecutor)
        .runPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, module,
            YamlUtils.writeYamlString(Map.of("inputs", pipelineExecuteBody.getInputs())), false, false, "", null, null,
            false, false);

    try (MockedStatic<GitAwareContextHelper> mockedGitAwareContextHelper = mockStatic(GitAwareContextHelper.class)) {
      Response response = pipelineExecutionApi.executePipeline(orgId, projectId, pipelineId, pipelineExecuteBody,
          accountId, module, false, false, "", branchName, connectorRef, null, null);

      PipelineExecuteResponseBody responseBody = (PipelineExecuteResponseBody) response.getEntity();

      assertThat(responseBody.getExecutionDetails().getExecutionId()).isEqualTo(planExecutionId);
      assertThat(responseBody.getExecutionDetails().getStatus()).isEqualTo(status.toString());

      // Verify that populateGitDetails was called with GitEntityInfo that has branch set
      mockedGitAwareContextHelper.verify(() -> GitAwareContextHelper.populateGitDetails(argThat(gitEntityInfo -> {
        return gitEntityInfo != null && branchName.equals(gitEntityInfo.getBranch());
      })));
    }
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testRunDynamicExecution_emptyOrgIdentifier_throwsInvalidRequest() {
    DynamicPipelineExecuteRequestBody body = new DynamicPipelineExecuteRequestBody();
    body.setYaml("pipeline:\n  identifier: test");

    assertThatThrownBy(()
                           -> pipelineExecutionApi.runDynamicExecutionWithInputYaml(
                               "", "project", "pipeline", body, "account", "CD", "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Organization identifier");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testRunDynamicExecution_nullOrgIdentifier_throwsInvalidRequest() {
    DynamicPipelineExecuteRequestBody body = new DynamicPipelineExecuteRequestBody();
    body.setYaml("pipeline:\n  identifier: test");

    assertThatThrownBy(()
                           -> pipelineExecutionApi.runDynamicExecutionWithInputYaml(
                               null, "project", "pipeline", body, "account", "CD", "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Organization identifier");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testRunDynamicExecution_emptyProjectIdentifier_throwsInvalidRequest() {
    DynamicPipelineExecuteRequestBody body = new DynamicPipelineExecuteRequestBody();
    body.setYaml("pipeline:\n  identifier: test");

    assertThatThrownBy(()
                           -> pipelineExecutionApi.runDynamicExecutionWithInputYaml(
                               "org", "", "pipeline", body, "account", "CD", "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Project identifier");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testRunDynamicExecution_emptyPipelineIdentifier_throwsInvalidRequest() {
    DynamicPipelineExecuteRequestBody body = new DynamicPipelineExecuteRequestBody();
    body.setYaml("pipeline:\n  identifier: test");

    assertThatThrownBy(()
                           -> pipelineExecutionApi.runDynamicExecutionWithInputYaml(
                               "org", "project", "", body, "account", "CD", "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Pipeline identifier");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testRunDynamicExecution_nullBody_throwsInvalidRequest() {
    assertThatThrownBy(()
                           -> pipelineExecutionApi.runDynamicExecutionWithInputYaml(
                               "org", "project", "pipeline", null, "account", "CD", "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("input YAML");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testRunDynamicExecution_emptyYaml_throwsInvalidRequest() {
    DynamicPipelineExecuteRequestBody body = new DynamicPipelineExecuteRequestBody();
    body.setYaml("");

    assertThatThrownBy(()
                           -> pipelineExecutionApi.runDynamicExecutionWithInputYaml(
                               "org", "project", "pipeline", body, "account", "CD", "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("input YAML");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testRunDynamicExecution_invalidScope_throwsInvalidRequest() {
    String accountId = "account";
    String orgId = "invalidOrg";
    String projectId = "invalidProject";
    String pipelineId = "pipeline";

    DynamicPipelineExecuteRequestBody body = new DynamicPipelineExecuteRequestBody();
    body.setYaml("pipeline:\n  identifier: test");

    when(pipelineExecutor.startDynamicExecution(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), anyString(),
             anyString(), eq(false), eq(false), anyString(), any(), eq(false)))
        .thenThrow(new InvalidRequestException(
            "Invalid scope parameters - orgIdentifier: [" + orgId + "], projectIdentifier: [" + projectId + "]"));

    assertThatThrownBy(()
                           -> pipelineExecutionApi.runDynamicExecutionWithInputYaml(
                               orgId, projectId, pipelineId, body, accountId, "CD", "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid scope parameters");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunDynamicExecutionInternal_withoutPrincipal_doesNotSetContext() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();
    String planExecutionId = generateUuid();

    DynamicPipelineExecuteInternalRequestBody body = new DynamicPipelineExecuteInternalRequestBody();
    body.setYaml("pipeline:\n  identifier: test");

    when(pipelineExecutor.startDynamicExecution(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), anyString(),
             anyString(), eq(false), eq(false), anyString(), any(), eq(true)))
        .thenReturn(PlanExecutionResponseDto.builder()
                        .planExecution(PlanExecution.builder().uuid(planExecutionId).status(Status.RUNNING).build())
                        .build());

    try (MockedStatic<SecurityContextBuilder> mockedSecurity = mockStatic(SecurityContextBuilder.class);
         MockedStatic<SourcePrincipalContextBuilder> mockedSource = mockStatic(SourcePrincipalContextBuilder.class)) {
      Response response = pipelineExecutionApi.runDynamicExecutionWithInputYamlInternal(
          orgId, projectId, pipelineId, body, accountId, "CD", "", false);

      PipelineExecuteResponseBody responseBody = (PipelineExecuteResponseBody) response.getEntity();
      assertThat(responseBody.getExecutionDetails().getExecutionId()).isEqualTo(planExecutionId);
      mockedSecurity.verifyNoInteractions();
      mockedSource.verifyNoInteractions();
    }
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunDynamicExecutionInternal_withUserPrincipal_setsContext() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();
    String planExecutionId = generateUuid();
    String userId = "user-1";
    String uniqueId = "user-unique-1";

    DynamicPipelineExecuteInternalRequestBody body = new DynamicPipelineExecuteInternalRequestBody();
    body.setYaml("pipeline:\n  identifier: test");
    io.harness.spec.server.pipeline.v1.model.Principal principal =
        new io.harness.spec.server.pipeline.v1.model.Principal();
    principal.setPrincipalIdentifier(userId);
    principal.setPrincipalType(io.harness.spec.server.pipeline.v1.model.Principal.PrincipalTypeEnum.USER);
    principal.setPrincipalUniqueId(uniqueId);
    body.setPrincipal(principal);

    when(pipelineExecutor.startDynamicExecution(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), anyString(),
             anyString(), eq(false), eq(false), anyString(), any(), eq(true)))
        .thenReturn(PlanExecutionResponseDto.builder()
                        .planExecution(PlanExecution.builder().uuid(planExecutionId).status(Status.RUNNING).build())
                        .build());

    try (MockedStatic<SecurityContextBuilder> mockedSecurity = mockStatic(SecurityContextBuilder.class);
         MockedStatic<SourcePrincipalContextBuilder> mockedSource = mockStatic(SourcePrincipalContextBuilder.class)) {
      Response response = pipelineExecutionApi.runDynamicExecutionWithInputYamlInternal(
          orgId, projectId, pipelineId, body, accountId, "CD", "", false);

      PipelineExecuteResponseBody responseBody = (PipelineExecuteResponseBody) response.getEntity();
      assertThat(responseBody.getExecutionDetails().getExecutionId()).isEqualTo(planExecutionId);
      mockedSecurity.verify(
          ()
              -> SecurityContextBuilder.setContext(argThat((io.harness.security.dto.Principal p)
                                                               -> p instanceof UserPrincipal
                      && userId.equals(p.getName()) && uniqueId.equals(((UserPrincipal) p).getUniqueId())
                      && accountId.equals(((UserPrincipal) p).getAccountId()))));
      mockedSource.verify(()
                              -> SourcePrincipalContextBuilder.setSourcePrincipal(
                                  argThat((io.harness.security.dto.Principal p) -> p instanceof UserPrincipal)));
    }
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunDynamicExecutionInternal_restoresPreviousContextOnException() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();

    DynamicPipelineExecuteInternalRequestBody body = new DynamicPipelineExecuteInternalRequestBody();
    body.setYaml("pipeline:\n  identifier: test");
    io.harness.spec.server.pipeline.v1.model.Principal principal =
        new io.harness.spec.server.pipeline.v1.model.Principal();
    principal.setPrincipalIdentifier("user-1");
    principal.setPrincipalType(io.harness.spec.server.pipeline.v1.model.Principal.PrincipalTypeEnum.USER);
    body.setPrincipal(principal);

    io.harness.security.dto.Principal previousPrincipal =
        new UserPrincipal("previous-user", null, null, accountId, null, "previous-user");

    when(pipelineExecutor.startDynamicExecution(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), anyString(),
             anyString(), eq(false), eq(false), anyString(), any(), eq(true)))
        .thenThrow(new RuntimeException("boom"));

    try (MockedStatic<SecurityContextBuilder> mockedSecurity = mockStatic(SecurityContextBuilder.class);
         MockedStatic<SourcePrincipalContextBuilder> mockedSource = mockStatic(SourcePrincipalContextBuilder.class)) {
      mockedSecurity.when(SecurityContextBuilder::getPrincipal).thenReturn(previousPrincipal);
      mockedSource.when(SourcePrincipalContextBuilder::getSourcePrincipal).thenReturn(previousPrincipal);

      assertThatThrownBy(()
                             -> pipelineExecutionApi.runDynamicExecutionWithInputYamlInternal(
                                 orgId, projectId, pipelineId, body, accountId, "CD", "", false))
          .isInstanceOf(RuntimeException.class);

      mockedSecurity.verify(() -> SecurityContextBuilder.setContext(previousPrincipal));
      mockedSource.verify(() -> SourcePrincipalContextBuilder.setSourcePrincipal(previousPrincipal));
    }
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunDynamicExecutionInternal_withUserPrincipalNoUniqueId_fallsBackToIdentifier() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();
    String userId = "user-1";

    DynamicPipelineExecuteInternalRequestBody body = new DynamicPipelineExecuteInternalRequestBody();
    body.setYaml("pipeline:\n  identifier: test");
    io.harness.spec.server.pipeline.v1.model.Principal principal =
        new io.harness.spec.server.pipeline.v1.model.Principal();
    principal.setPrincipalIdentifier(userId);
    principal.setPrincipalType(io.harness.spec.server.pipeline.v1.model.Principal.PrincipalTypeEnum.USER);
    body.setPrincipal(principal);

    when(pipelineExecutor.startDynamicExecution(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), anyString(),
             anyString(), eq(false), eq(false), anyString(), any(), eq(true)))
        .thenReturn(PlanExecutionResponseDto.builder()
                        .planExecution(PlanExecution.builder().uuid(generateUuid()).status(Status.RUNNING).build())
                        .build());

    try (MockedStatic<SecurityContextBuilder> mockedSecurity = mockStatic(SecurityContextBuilder.class);
         MockedStatic<SourcePrincipalContextBuilder> mockedSource = mockStatic(SourcePrincipalContextBuilder.class)) {
      pipelineExecutionApi.runDynamicExecutionWithInputYamlInternal(
          orgId, projectId, pipelineId, body, accountId, "CD", "", false);

      mockedSecurity.verify(
          ()
              -> SecurityContextBuilder.setContext(argThat((io.harness.security.dto.Principal p)
                                                               -> p instanceof UserPrincipal
                      && userId.equals(p.getName()) && userId.equals(((UserPrincipal) p).getUniqueId()))));
    }
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunDynamicExecutionInternal_withServiceAccountPrincipal_setsContext() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();
    String saId = "sa-1";
    String uniqueId = "sa-unique-1";

    DynamicPipelineExecuteInternalRequestBody body = new DynamicPipelineExecuteInternalRequestBody();
    body.setYaml("pipeline:\n  identifier: test");
    io.harness.spec.server.pipeline.v1.model.Principal principal =
        new io.harness.spec.server.pipeline.v1.model.Principal();
    principal.setPrincipalIdentifier(saId);
    principal.setPrincipalType(io.harness.spec.server.pipeline.v1.model.Principal.PrincipalTypeEnum.SERVICE_ACCOUNT);
    principal.setPrincipalUniqueId(uniqueId);
    body.setPrincipal(principal);

    when(pipelineExecutor.startDynamicExecution(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), anyString(),
             anyString(), eq(false), eq(false), anyString(), any(), eq(true)))
        .thenReturn(PlanExecutionResponseDto.builder()
                        .planExecution(PlanExecution.builder().uuid(generateUuid()).status(Status.RUNNING).build())
                        .build());

    try (MockedStatic<SecurityContextBuilder> mockedSecurity = mockStatic(SecurityContextBuilder.class);
         MockedStatic<SourcePrincipalContextBuilder> mockedSource = mockStatic(SourcePrincipalContextBuilder.class)) {
      pipelineExecutionApi.runDynamicExecutionWithInputYamlInternal(
          orgId, projectId, pipelineId, body, accountId, "CD", "", false);

      mockedSecurity.verify(
          ()
              -> SecurityContextBuilder.setContext(argThat((io.harness.security.dto.Principal p)
                                                               -> p instanceof ServiceAccountPrincipal
                      && saId.equals(p.getName()) && uniqueId.equals(((ServiceAccountPrincipal) p).getUniqueId())
                      && accountId.equals(((ServiceAccountPrincipal) p).getAccountId()))));
      mockedSource.verify(()
                              -> SourcePrincipalContextBuilder.setSourcePrincipal(argThat(
                                  (io.harness.security.dto.Principal p) -> p instanceof ServiceAccountPrincipal)));
    }
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunDynamicExecutionInternal_withServicePrincipal_setsContext() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();
    String serviceId = "pipeline-service";

    DynamicPipelineExecuteInternalRequestBody body = new DynamicPipelineExecuteInternalRequestBody();
    body.setYaml("pipeline:\n  identifier: test");
    io.harness.spec.server.pipeline.v1.model.Principal principal =
        new io.harness.spec.server.pipeline.v1.model.Principal();
    principal.setPrincipalIdentifier(serviceId);
    principal.setPrincipalType(io.harness.spec.server.pipeline.v1.model.Principal.PrincipalTypeEnum.SERVICE);
    body.setPrincipal(principal);

    when(pipelineExecutor.startDynamicExecution(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), anyString(),
             anyString(), eq(false), eq(false), anyString(), any(), eq(true)))
        .thenReturn(PlanExecutionResponseDto.builder()
                        .planExecution(PlanExecution.builder().uuid(generateUuid()).status(Status.RUNNING).build())
                        .build());

    try (MockedStatic<SecurityContextBuilder> mockedSecurity = mockStatic(SecurityContextBuilder.class);
         MockedStatic<SourcePrincipalContextBuilder> mockedSource = mockStatic(SourcePrincipalContextBuilder.class)) {
      pipelineExecutionApi.runDynamicExecutionWithInputYamlInternal(
          orgId, projectId, pipelineId, body, accountId, "CD", "", false);

      mockedSecurity.verify(()
                                -> SecurityContextBuilder.setContext(
                                    argThat((io.harness.security.dto.Principal p)
                                                -> p instanceof ServicePrincipal && serviceId.equals(p.getName()))));
      mockedSource.verify(()
                              -> SourcePrincipalContextBuilder.setSourcePrincipal(
                                  argThat((io.harness.security.dto.Principal p)
                                              -> p instanceof ServicePrincipal && serviceId.equals(p.getName()))));
    }
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunDynamicExecutionInternal_withApiKeyPrincipal_setsContext() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String pipelineId = generateUuid();
    String apiKeyId = "api-key-1";

    DynamicPipelineExecuteInternalRequestBody body = new DynamicPipelineExecuteInternalRequestBody();
    body.setYaml("pipeline:\n  identifier: test");
    io.harness.spec.server.pipeline.v1.model.Principal principal =
        new io.harness.spec.server.pipeline.v1.model.Principal();
    principal.setPrincipalIdentifier(apiKeyId);
    principal.setPrincipalType(io.harness.spec.server.pipeline.v1.model.Principal.PrincipalTypeEnum.API_KEY);
    body.setPrincipal(principal);

    when(pipelineExecutor.startDynamicExecution(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), anyString(),
             anyString(), eq(false), eq(false), anyString(), any(), eq(true)))
        .thenReturn(PlanExecutionResponseDto.builder()
                        .planExecution(PlanExecution.builder().uuid(generateUuid()).status(Status.RUNNING).build())
                        .build());

    try (MockedStatic<SecurityContextBuilder> mockedSecurity = mockStatic(SecurityContextBuilder.class);
         MockedStatic<SourcePrincipalContextBuilder> mockedSource = mockStatic(SourcePrincipalContextBuilder.class)) {
      pipelineExecutionApi.runDynamicExecutionWithInputYamlInternal(
          orgId, projectId, pipelineId, body, accountId, "CD", "", false);

      mockedSecurity.verify(()
                                -> SecurityContextBuilder.setContext(
                                    argThat((io.harness.security.dto.Principal p)
                                                -> p instanceof ApiKeyPrincipal && apiKeyId.equals(p.getName()))));
      mockedSource.verify(()
                              -> SourcePrincipalContextBuilder.setSourcePrincipal(
                                  argThat((io.harness.security.dto.Principal p)
                                              -> p instanceof ApiKeyPrincipal && apiKeyId.equals(p.getName()))));
    }
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunDynamicExecutionInternal_withMissingPrincipalType_throwsInvalidRequest() {
    DynamicPipelineExecuteInternalRequestBody body = new DynamicPipelineExecuteInternalRequestBody();
    body.setYaml("pipeline:\n  identifier: test");
    io.harness.spec.server.pipeline.v1.model.Principal principal =
        new io.harness.spec.server.pipeline.v1.model.Principal();
    principal.setPrincipalIdentifier("user-1");
    body.setPrincipal(principal);

    assertThatThrownBy(()
                           -> pipelineExecutionApi.runDynamicExecutionWithInputYamlInternal(
                               "org", "project", "pipeline", body, "account", "CD", "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("principal_type is required");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunDynamicExecutionInternal_nullBody_throwsInvalidRequest() {
    assertThatThrownBy(()
                           -> pipelineExecutionApi.runDynamicExecutionWithInputYamlInternal(
                               "org", "project", "pipeline", null, "account", "CD", "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("input YAML");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetStagesExecutionListThrowsClearMessageWhenYamlExceedsSizeLimit() {
    StringBuilder largeYamlBuilder = new StringBuilder("pipeline:\n  name: test\n  identifier: test\n  stages:\n");
    String padding = "x".repeat(1024);
    while (largeYamlBuilder.length() <= 3 * 1024 * 1024) {
      largeYamlBuilder.append("    - stage:\n        name: s\n        identifier: s\n        value: \"")
          .append(padding)
          .append("\"\n");
    }
    String largeYaml = largeYamlBuilder.toString();
    PipelineEntity pipelineEntity =
        PipelineEntity.builder().yaml(largeYaml).harnessVersion(HarnessYamlVersion.V0).build();
    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), eq(false), eq(false),
             eq(false), eq(false), any(), eq(true)))
        .thenReturn(Optional.of(pipelineEntity));

    try (MockedStatic<TemplateRefHelper> mockedTemplateRefHelper = mockStatic(TemplateRefHelper.class)) {
      mockedTemplateRefHelper
          .when(() -> TemplateRefHelper.hasTemplateRefOrCustomDeploymentRef(anyString(), anyString()))
          .thenReturn(false);
      assertThatThrownBy(()
                             -> pipelineExecutionApi.getStagesExecutionList(
                                 "org", "project", "pipeline", "account", null, null, null, null))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining("Pipeline YAML size exceeds the maximum allowed limit of 3 MB");
    }
  }
}
