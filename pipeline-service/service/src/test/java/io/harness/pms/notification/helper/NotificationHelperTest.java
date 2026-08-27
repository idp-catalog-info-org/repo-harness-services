/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.helper;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.notification.PipelineEventType.ALL_EVENTS;
import static io.harness.notification.PipelineEventType.PIPELINE_FAILED;
import static io.harness.notification.PipelineEventType.PIPELINE_START;
import static io.harness.notification.PipelineEventType.PIPELINE_SUCCESS;
import static io.harness.notification.PipelineEventType.STAGE_FAILED;
import static io.harness.notification.PipelineEventType.STAGE_SUCCESS;
import static io.harness.notification.PipelineEventType.WAITING_FOR_USER_ACTION;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.DANIEL;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.constants.JsonConstants;
import io.harness.category.element.UnitTests;
import io.harness.dto.FailureInfoDTO;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.eraro.ResponseMessage;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionBuilder;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.expression.common.ExpressionMode;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ng.core.cdstage.CDStageSummaryResponseDTO;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.notification.NotificationConstants;
import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.PipelineEventType;
import io.harness.notification.bean.NotificationChannelWrapper;
import io.harness.notification.bean.NotificationRules;
import io.harness.notification.bean.PipelineEvent;
import io.harness.notification.channelDetails.NotificationChannelType;
import io.harness.notification.channelDetails.PmsWebhookChannel;
import io.harness.notification.channeldetails.EmailChannel;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.channeldetails.WebhookChannel;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.notificationclient.NotificationClientImpl;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.plan.PlanNode;
import io.harness.pms.approval.notification.stagemetadata.StageMetadataNotificationHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyInfo;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.notification.NotificationRulesMapper;
import io.harness.pms.notification.WebhookNotificationServiceImpl;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.sanitizer.HtmlInputSanitizer;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class NotificationHelperTest extends CategoryTest {
  @Mock NotificationEventsHelper notificationEventsHelper;
  @Mock PersistentLocker persistentLocker;
  NotificationClient notificationClient;
  NotificationRulesMapper notificationRulesMapper;
  NodeExecutionService nodeExecutionService;
  PlanExecutionService planExecutionService;
  PipelineServiceConfiguration pipelineServiceConfiguration;
  PlanExecutionMetadataService planExecutionMetadataService;
  NotificationHelper notificationHelper;
  PmsEngineExpressionService pmsEngineExpressionService;
  PMSPipelineService pmsPipelineService;
  PipelineExpressionHelper pipelineExpressionHelper;
  HtmlInputSanitizer htmlInputSanitizer;
  PMSExecutionService pmsExecutionService;
  PmsFeatureFlagHelper pmsFeatureFlagHelper;
  StageMetadataNotificationHelper stageMetadataNotificationHelper;
  PMSPipelineTemplateHelper pipelineTemplateHelper;
  String executionUrl = "http:127.0.0.1:8080/account/dummyAccount/cd/orgs/dummyOrg/projects/dummyProject/pipelines/"
      + "dummyPipeline/executions/dummyPlanExecutionId/pipeline";
  PlanNode stagePlanNode = PlanNode.builder()
                               .uuid(generateUuid())
                               .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                               .identifier("dummyIdentifier")
                               .build();
  Ambiance ambiance =
      Ambiance.newBuilder()
          .putSetupAbstractions("accountId", "dummyAccount")
          .putSetupAbstractions("orgIdentifier", "dummyOrg")
          .putSetupAbstractions("projectIdentifier", "dummyProject")
          .setMetadata(
              ExecutionMetadata.newBuilder()
                  .setModuleType("cd")
                  .setIsNotificationConfigured(true)
                  .setPipelineIdentifier("dummyPipeline")
                  .setTriggerInfo(
                      io.harness.pms.contracts.plan.ExecutionTriggerInfo.newBuilder()
                          .setTriggeredBy(
                              io.harness.pms.contracts.plan.TriggeredBy.newBuilder().setIdentifier("dummy").build())
                          .build())
                  .build())
          .setPlanExecutionId("dummyPlanExecutionId")
          .setStageExecutionId("dummyStageExecutionId")
          .addLevels(PmsLevelUtils.buildLevelFromNode("dummyStageId", stagePlanNode))
          .build();
  PipelineEventType pipelineEventType = PipelineEventType.PIPELINE_END;
  Long updatedAt = 0L;
  String yaml = "pipeline:\n"
      + "    name: DockerTest\n"
      + "    identifier: DockerTest\n"
      + "    notificationRules:\n"
      + "        - name: N2\n"
      + "          pipelineEvents:\n"
      + "              - type: PipelineSuccess\n"
      + "              - type: StageFailed\n"
      + "                forStages:\n"
      + "                    - stage1\n"
      + "          notificationMethod:\n"
      + "              type: Slack\n"
      + "              spec:\n"
      + "                  userGroups: []\n"
      + "                  webhookUrl: "
      + "https://hooks.slack.com/services/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\n"
      + "          enabled: true\n";
  String emailNotificationYaml = "pipeline:\n"
      + "    name: DockerTest\n"
      + "    identifier: DockerTest\n"
      + "    notificationRules:\n"
      + "        - name: N2\n"
      + "          pipelineEvents:\n"
      + "              - type: PipelineSuccess\n"
      + "              - type: StageFailed\n"
      + "                forStages:\n"
      + "                    - stage1\n"
      + "          notificationMethod:\n"
      + "              type: Email\n"
      + "              spec:\n"
      + "                  userGroups: []\n"
      + "                  recipients: \n"
      + "                    - admin@harness.io \n"
      + "                    - test@harness.io \n"
      + "          enabled: true\n";
  String webhookNotificationYaml = "pipeline:\n"
      + "    name: DockerTest\n"
      + "    identifier: DockerTest\n"
      + "    notificationRules:\n"
      + "        - name: N2\n"
      + "          pipelineEvents:\n"
      + "              - type: PipelineSuccess\n"
      + "              - type: StageFailed\n"
      + "                forStages:\n"
      + "                    - stage1\n"
      + "          notificationMethod:\n"
      + "              type: Webhook\n"
      + "              spec:\n"
      + "                  webhookUrl: https://www.google.com\n"
      + "          enabled: true\n";
  String allEventsYaml = "pipeline:\n"
      + "    name: DockerTest\n"
      + "    identifier: DockerTest\n"
      + "    notificationRules:\n"
      + "        - name: N2\n"
      + "          pipelineEvents:\n"
      + "              - type: AllEvents\n"
      + "          notificationMethod:\n"
      + "              type: Email\n"
      + "              spec:\n"
      + "                  userGroups: []\n"
      + "                  recipients: \n"
      + "                    - admin@harness.io \n"
      + "                    - test@harness.io \n"
      + "          enabled: true\n";

  String notificationRulesString =
      "{\"__recast\":\"java.util.ArrayList\",\"__encodedValue\":[{\"__recast\":\"io.harness.notification.bean."
      + "NotificationRules\",\"name\":\"N2\",\"enabled\":true,\"pipelineEvents\":[{\"__recast\":\"io.harness."
      + "notification.bean.PipelineEvent\",\"type\":\"ALL_EVENTS\",\"forStages\":null},{\"__recast\":\"io.harness."
      + "notification.bean.PipelineEvent\",\"type\":\"STAGE_FAILED\",\"forStages\":[\"stage1\"]}],"
      + "\"notificationChannelWrapper\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness."
      + "serializer.recaster.ParameterDocumentField\",\"expressionValue\":null,\"expression\":false,\"valueDoc\":{\"__"
      + "recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\",\"value\":{\"__recast\":\"io.harness.notification."
      + "bean.NotificationChannelWrapper\",\"type\":\"Email\",\"notificationChannel\":{\"__recast\":\"io.harness."
      + "notification.channelDetails.PmsEmailChannel\",\"userGroups\":[],\"recipients\":[\"admin@harness.io\",\"test@"
      + "harness.io\"]}}},\"valueClass\":\"io.harness.notification.bean.NotificationChannelWrapper\",\"typeString\":"
      + "false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false,\"responseField\":null}}}]}";
  Map<String, Object> notificationRulesMap =
      RecastOrchestrationUtils.toMap(RecastOrchestrationUtils.fromJson(notificationRulesString));
  String webhookNotificationRulesString =
      "{\"__recast\":\"java.util.ArrayList\",\"__encodedValue\":[{\"__recast\":\"io.harness.notification.bean."
      + "NotificationRules\",\"name\":\"N2\",\"enabled\":true,\"pipelineEvents\":[{\"__recast\":\"io.harness."
      + "notification.bean.PipelineEvent\",\"type\":\"PIPELINE_SUCCESS\"},{\"__recast\":\"io.harness.notification.bean."
      + "PipelineEvent\",\"type\":\"STAGE_FAILED\",\"forStages\":[\"stage1\"]}],\"notificationChannelWrapper\":{\"__"
      + "recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.serializer.recaster."
      + "ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml."
      + "ParameterFieldValueWrapper\",\"value\":{\"__recast\":\"io.harness.notification.bean."
      + "NotificationChannelWrapper\",\"type\":\"Webhook\",\"notificationChannel\":{\"__recast\":\"io.harness."
      + "notification.channelDetails.PmsWebhookChannel\",\"webhookUrl\":{\"__recast\":\"parameterField\",\"__"
      + "encodedValue\":{\"__recast\":\"io.harness.serializer.recaster.ParameterDocumentField\",\"expression\":false,"
      + "\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\",\"value\":\"https://"
      + "www.google.com\"},\"valueClass\":\"java.lang.String\",\"typeString\":true,\"skipAutoEvaluation\":false,"
      + "\"jsonResponseField\":false}}}}},\"valueClass\":\"io.harness.notification.bean.NotificationChannelWrapper\","
      + "\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}}}]}";
  Map<String, Object> webhookNotificationRulesMap =
      RecastOrchestrationUtils.toMap(RecastOrchestrationUtils.fromJson(webhookNotificationRulesString));

  private static final String ACCOUNT_IDENTIFIER = "ACCOUNT_IDENTIFIER";
  private static final String ORG_IDENTIFIER = "ORG_IDENTIFIER";
  private static final String PROJECT_IDENTIFIER = "PROJECT_IDENTIFIER";
  private static final String EVENT_FILTERING_NOTIFICATION_YAML = "pipeline:\n"
      + "  name: TestPipeline\n"
      + "  identifier: TestPipeline\n"
      + "  notificationRules:\n"
      + "    - name: failure-rule\n"
      + "      pipelineEvents:\n"
      + "        - type: PipelineFailed\n"
      + "      notificationMethod:\n"
      + "        type: Email\n"
      + "        spec:\n"
      + "          recipients:\n"
      + "            - failure@harness.io\n"
      + "      enabled: true\n"
      + "    - name: success-rule\n"
      + "      pipelineEvents:\n"
      + "        - type: PipelineSuccess\n"
      + "      notificationMethod:\n"
      + "        type: Email\n"
      + "        spec:\n"
      + "          recipients:\n"
      + "            - success@harness.io\n"
      + "      enabled: true\n";
  private static final String MULTIPLE_ELIGIBLE_NOTIFICATION_YAML =
      EVENT_FILTERING_NOTIFICATION_YAML.replace("PipelineSuccess", "PipelineFailed");
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
  public void setup() {
    notificationClient = mock(NotificationClientImpl.class);
    nodeExecutionService = mock(NodeExecutionService.class);
    planExecutionService = mock(PlanExecutionService.class);
    pipelineServiceConfiguration = mock(PipelineServiceConfiguration.class);
    planExecutionMetadataService = mock(PlanExecutionMetadataService.class);
    pmsEngineExpressionService = mock(PmsEngineExpressionService.class);
    pmsPipelineService = mock(PMSPipelineService.class);
    pipelineExpressionHelper = mock(PipelineExpressionHelper.class);
    htmlInputSanitizer = mock(HtmlInputSanitizer.class);
    pmsExecutionService = mock(PMSExecutionService.class);
    pmsFeatureFlagHelper = mock(PmsFeatureFlagHelper.class);
    notificationEventsHelper = mock(NotificationEventsHelper.class);
    notificationRulesMapper = mock(NotificationRulesMapper.class);
    pipelineTemplateHelper = mock(PMSPipelineTemplateHelper.class);
    notificationHelper = spy(new NotificationHelper());
    WebhookNotificationServiceImpl webhookNotificationService = mock(WebhookNotificationServiceImpl.class);
    stageMetadataNotificationHelper = mock(StageMetadataNotificationHelper.class);
    notificationHelper.notificationClient = notificationClient;
    notificationHelper.nodeExecutionService = nodeExecutionService;
    notificationHelper.planExecutionService = planExecutionService;
    notificationHelper.pipelineServiceConfiguration = pipelineServiceConfiguration;
    notificationHelper.planExecutionMetadataService = planExecutionMetadataService;
    notificationHelper.pmsEngineExpressionService = pmsEngineExpressionService;
    notificationHelper.pmsPipelineService = pmsPipelineService;
    notificationHelper.pipelineExpressionHelper = pipelineExpressionHelper;
    notificationHelper.userNameSanitizer = htmlInputSanitizer;
    notificationHelper.pmsExecutionService = pmsExecutionService;
    notificationHelper.webhookNotificationService = webhookNotificationService;
    notificationHelper.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
    notificationHelper.stageMetadataNotificationHelper = stageMetadataNotificationHelper;
    notificationHelper.notificationEventsHelper = notificationEventsHelper;
    notificationHelper.notificationRulesMapper = notificationRulesMapper;
    notificationHelper.pipelineTemplateHelper = pipelineTemplateHelper;
    try {
      Field persistentLockerField = NotificationHelper.class.getDeclaredField("persistentLocker");
      persistentLockerField.setAccessible(true);
      persistentLockerField.set(notificationHelper, persistentLocker);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set persistentLocker field", e);
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetNotificationRulesForEventWhenFeatureFlagDisabledResolvesAllRules() throws Exception {
    when(pmsFeatureFlagHelper.isEnabled("dummyAccount", FeatureName.PIPE_FILTER_NOTIFICATION_RULES_BY_EVENT_TYPE))
        .thenReturn(false);
    when(pmsEngineExpressionService.resolve(eq(ambiance), any(), eq(true)))
        .thenAnswer(invocation -> invocation.getArgument(1));

    List<NotificationRules> notificationRules = notificationHelper.getNotificationRulesForEvent(
        EVENT_FILTERING_NOTIFICATION_YAML, ambiance, PIPELINE_FAILED, "", null);

    assertThat(notificationRules)
        .extracting(NotificationRules::getName)
        .containsExactly("failure-rule", "success-rule");
    verify(pmsEngineExpressionService, times(1)).resolve(eq(ambiance), any(), eq(true));
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetNotificationRulesForEventWhenFeatureFlagEnabledResolvesOnlyEligibleRules() throws Exception {
    when(pmsFeatureFlagHelper.isEnabled("dummyAccount", FeatureName.PIPE_FILTER_NOTIFICATION_RULES_BY_EVENT_TYPE))
        .thenReturn(true);
    when(pmsEngineExpressionService.resolve(eq(ambiance), any(), eq(true)))
        .thenAnswer(invocation -> invocation.getArgument(1));

    List<NotificationRules> notificationRules = notificationHelper.getNotificationRulesForEvent(
        EVENT_FILTERING_NOTIFICATION_YAML, ambiance, PIPELINE_FAILED, "", null);

    assertThat(notificationRules).extracting(NotificationRules::getName).containsExactly("failure-rule");
    verify(pmsEngineExpressionService, times(1)).resolve(eq(ambiance), any(), eq(true));
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetNotificationRulesForEventResolvesEligibleRulesIndividually() throws Exception {
    when(pmsFeatureFlagHelper.isEnabled("dummyAccount", FeatureName.PIPE_FILTER_NOTIFICATION_RULES_BY_EVENT_TYPE))
        .thenReturn(true);
    when(pmsEngineExpressionService.resolve(eq(ambiance), any(), eq(true)))
        .thenAnswer(invocation -> invocation.getArgument(1));

    List<NotificationRules> notificationRules = notificationHelper.getNotificationRulesForEvent(
        MULTIPLE_ELIGIBLE_NOTIFICATION_YAML, ambiance, PIPELINE_FAILED, "", null);

    assertThat(notificationRules)
        .extracting(NotificationRules::getName)
        .containsExactly("failure-rule", "success-rule");
    verify(pmsEngineExpressionService, times(2)).resolve(eq(ambiance), any(), eq(true));
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetNotificationRulesForEventSkipsRuleWhenItsExpressionResolutionFails() throws Exception {
    when(pmsFeatureFlagHelper.isEnabled("dummyAccount", FeatureName.PIPE_FILTER_NOTIFICATION_RULES_BY_EVENT_TYPE))
        .thenReturn(true);
    when(pmsEngineExpressionService.resolve(eq(ambiance), any(), eq(true)))
        .thenThrow(new RuntimeException("expression resolution failed"))
        .thenAnswer(invocation -> invocation.getArgument(1));

    List<NotificationRules> notificationRules = notificationHelper.getNotificationRulesForEvent(
        MULTIPLE_ELIGIBLE_NOTIFICATION_YAML, ambiance, PIPELINE_FAILED, "", null);

    assertThat(notificationRules).extracting(NotificationRules::getName).containsExactly("success-rule");
    verify(pmsEngineExpressionService, times(2)).resolve(eq(ambiance), any(), eq(true));
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetNotificationRulesForEventWhenFeatureFlagEnabledSkipsResolveWithoutEligibleRules()
      throws Exception {
    when(pmsFeatureFlagHelper.isEnabled("dummyAccount", FeatureName.PIPE_FILTER_NOTIFICATION_RULES_BY_EVENT_TYPE))
        .thenReturn(true);

    List<NotificationRules> notificationRules = notificationHelper.getNotificationRulesForEvent(
        EVENT_FILTERING_NOTIFICATION_YAML, ambiance, PipelineEventType.STAGE_START, "stage", null);

    assertThat(notificationRules).isEmpty();
    verify(pmsEngineExpressionService, never()).resolve(eq(ambiance), any(), eq(true));
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetNotificationRulesForGraphObserverDoesNotResolveExpressionsWhenFeatureFlagEnabled() {
    NodeExecution nodeExecution = NodeExecution.builder().ambiance(ambiance).build();
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(ambiance);
    when(pmsFeatureFlagHelper.isEnabled("dummyAccount", FeatureName.PIPE_FILTER_NOTIFICATION_RULES_BY_EVENT_TYPE))
        .thenReturn(true);

    List<NotificationRules> notificationRules =
        notificationHelper.getNotificationRules(nodeExecution, EVENT_FILTERING_NOTIFICATION_YAML);

    assertThat(notificationRules)
        .extracting(NotificationRules::getName)
        .containsExactly("failure-rule", "success-rule");
    verify(pmsEngineExpressionService, never()).resolve(eq(ambiance), any(), eq(true));
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetNotificationRulesForEventFiltersV1RulesBeforeResolution() throws Exception {
    String v1Yaml = "pipeline:\n"
        + "  name: TestPipeline\n"
        + "  id: TestPipeline\n"
        + "  notifications:\n"
        + "    - id: failure-rule\n"
        + "      name: failure-rule\n"
        + "      \"on\":\n"
        + "        - pipeline: failed\n"
        + "      uses: webhook\n"
        + "      with:\n"
        + "        url: failure-webhook\n"
        + "      disabled: false\n"
        + "    - id: success-rule\n"
        + "      name: success-rule\n"
        + "      \"on\":\n"
        + "        - pipeline: success\n"
        + "      uses: webhook\n"
        + "      with:\n"
        + "        url: success-webhook\n"
        + "      disabled: false\n";
    Ambiance v1Ambiance = ambiance.toBuilder()
                              .setMetadata(ambiance.getMetadata().toBuilder().setHarnessVersion(HarnessYamlVersion.V1))
                              .build();
    notificationHelper.notificationRulesMapper = new NotificationRulesMapper();
    when(pmsFeatureFlagHelper.isEnabled("dummyAccount", FeatureName.PIPE_FILTER_NOTIFICATION_RULES_BY_EVENT_TYPE))
        .thenReturn(true);
    when(pmsEngineExpressionService.resolve(eq(v1Ambiance), any(), eq(true)))
        .thenAnswer(invocation -> invocation.getArgument(1));

    List<NotificationRules> notificationRules =
        notificationHelper.getNotificationRulesForEvent(v1Yaml, v1Ambiance, PIPELINE_FAILED, "", null);

    assertThat(notificationRules).extracting(NotificationRules::getName).containsExactly("failure-rule");
    verify(pmsEngineExpressionService, times(1)).resolve(eq(v1Ambiance), any(), eq(true));
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetNotificationRulesForEventIsolatesV1ExpressionResolutionFailures() throws Exception {
    String v1Yaml = "pipeline:\n"
        + "  name: TestPipeline\n"
        + "  id: TestPipeline\n"
        + "  notifications:\n"
        + "    - id: first-rule\n"
        + "      name: first-rule\n"
        + "      \"on\":\n"
        + "        - pipeline: failed\n"
        + "      uses: webhook\n"
        + "      with:\n"
        + "        url: first-webhook\n"
        + "      disabled: false\n"
        + "    - id: second-rule\n"
        + "      name: second-rule\n"
        + "      \"on\":\n"
        + "        - pipeline: failed\n"
        + "      uses: webhook\n"
        + "      with:\n"
        + "        url: second-webhook\n"
        + "      disabled: false\n";
    Ambiance v1Ambiance = ambiance.toBuilder()
                              .setMetadata(ambiance.getMetadata().toBuilder().setHarnessVersion(HarnessYamlVersion.V1))
                              .build();
    notificationHelper.notificationRulesMapper = new NotificationRulesMapper();
    when(pmsFeatureFlagHelper.isEnabled("dummyAccount", FeatureName.PIPE_FILTER_NOTIFICATION_RULES_BY_EVENT_TYPE))
        .thenReturn(true);
    when(pmsEngineExpressionService.resolve(eq(v1Ambiance), any(), eq(true)))
        .thenThrow(new RuntimeException("expression resolution failed"))
        .thenAnswer(invocation -> invocation.getArgument(1));

    List<NotificationRules> notificationRules =
        notificationHelper.getNotificationRulesForEvent(v1Yaml, v1Ambiance, PIPELINE_FAILED, "", null);

    assertThat(notificationRules).extracting(NotificationRules::getName).containsExactly("second-rule");
    verify(pmsEngineExpressionService, times(2)).resolve(eq(v1Ambiance), any(), eq(true));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGenerateUrl() {
    when(pipelineServiceConfiguration.getPipelineServiceBaseUrl()).thenReturn("http:127.0.0.1:8080");
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder().build();
    doReturn(executionUrl).when(pipelineExpressionHelper).generateUrl(ambiance, pipelineExecutionSummaryEntity);
    String generatedUrl = notificationHelper.generateUrl(ambiance, pipelineExecutionSummaryEntity);
    assertEquals(executionUrl, generatedUrl);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSendNotification() {
    PlanNode planNode = PlanNode.builder().identifier("dummyIdentifier").build();
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().yaml(yaml).build();
    NodeExecution nodeExecution =
        NodeExecution.builder().status(Status.SUCCEEDED).startTs(0L).ambiance(ambiance).build();
    when(planExecutionMetadataService.findByPlanExecutionId(any(), any()))
        .thenReturn(java.util.Optional.ofNullable(planExecutionMetadata));
    doReturn(null).when(notificationClient).sendNotificationAsync(any());
    when(planExecutionService.getPlanExecutionMetadata(anyString()))
        .thenReturn(PlanExecution.builder().status(Status.SUCCEEDED).startTs(0L).endTs(0L).build());
    doReturn(executionUrl)
        .when(notificationHelper)
        .generateUrl(any(Ambiance.class), any(PipelineExecutionSummaryEntity.class));
    when(pmsExecutionService.fetchExecutionSummaryFromDb(any(), any()))
        .thenReturn(PipelineExecutionSummaryEntity.builder()
                        .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                  .setTriggerType(TriggerType.MANUAL)
                                                  .setTriggeredBy(TriggeredBy.newBuilder()
                                                                      .setIdentifier("user")
                                                                      .putExtraInfo("email", "user@harness.io")
                                                                      .build())
                                                  .build())
                        .build());
    // testing pipeline level event flow.
    assertThatCode(()
                       -> notificationHelper.sendNotification(
                           ambiance, PipelineEventType.PIPELINE_SUCCESS, nodeExecution, updatedAt))
        .doesNotThrowAnyException();
    // testing stage level(non pipeline) flow.
    assertThatCode(
        () -> notificationHelper.sendNotification(ambiance, PipelineEventType.STAGE_FAILED, nodeExecution, updatedAt))
        .doesNotThrowAnyException();
  }
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetEventTypeForStage() {
    String planExecutionId = generateUuid();
    PlanNode pipelinePlanNode = PlanNode.builder()
                                    .uuid(generateUuid())
                                    .stepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).build())
                                    .identifier("dummyIdentifier")
                                    .build();
    PlanNode stagePlanNode = PlanNode.builder()
                                 .uuid(generateUuid())
                                 .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                 .identifier("dummyIdentifier")
                                 .build();

    Ambiance.Builder ambianceBuilder =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), pipelinePlanNode));
    NodeExecutionBuilder nodeExecutionBuilder =
        NodeExecution.builder().ambiance(ambianceBuilder.build()).status(Status.SUCCEEDED);
    assertEquals(notificationHelper.getEventTypeForStage(nodeExecutionBuilder.build()), Optional.empty());
    ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagePlanNode));
    assertEquals(
        notificationHelper.getEventTypeForStage(nodeExecutionBuilder.ambiance(ambianceBuilder.build()).build()),
        Optional.of(STAGE_SUCCESS));
    nodeExecutionBuilder.status(Status.IGNORE_FAILED);
    assertEquals(notificationHelper.getEventTypeForStage(nodeExecutionBuilder.build()), Optional.of(STAGE_SUCCESS));

    nodeExecutionBuilder.status(Status.FAILED);
    assertEquals(notificationHelper.getEventTypeForStage(nodeExecutionBuilder.build()), Optional.of(STAGE_FAILED));
    nodeExecutionBuilder.status(Status.ABORTED);
    assertEquals(notificationHelper.getEventTypeForStage(nodeExecutionBuilder.build()), Optional.of(STAGE_FAILED));

    nodeExecutionBuilder.status(Status.RUNNING);
    assertEquals(notificationHelper.getEventTypeForStage(nodeExecutionBuilder.build()), Optional.empty());
    nodeExecutionBuilder.status(Status.SKIPPED);
    assertEquals(notificationHelper.getEventTypeForStage(nodeExecutionBuilder.build()), Optional.empty());
  }

  // TODO: Add ut for webhookNotification once PL PR is merged.
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testEmailNotificationIsSentToAllRecipients() {
    PlanNode planNode = PlanNode.builder().identifier("dummyIdentifier").build();
    NodeExecution nodeExecution =
        NodeExecution.builder().status(Status.SUCCEEDED).startTs(0L).ambiance(ambiance).build();
    when(planExecutionService.getPlanExecutionMetadata(anyString()))
        .thenReturn(PlanExecution.builder().status(Status.SUCCEEDED).startTs(0L).endTs(0L).build());
    when(planExecutionMetadataService.findByPlanExecutionId(anyString(), anyString()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().yaml(emailNotificationYaml).build()));
    when(htmlInputSanitizer.sanitizeInput(any())).thenReturn("dummy");
    when(pmsExecutionService.fetchExecutionSummaryFromDb(any(), any()))
        .thenReturn(PipelineExecutionSummaryEntity.builder()
                        .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                  .setTriggerType(TriggerType.MANUAL)
                                                  .setTriggeredBy(TriggeredBy.newBuilder()
                                                                      .setIdentifier("user")
                                                                      .putExtraInfo("email", "user@harness.io")
                                                                      .build())
                                                  .build())
                        .build());
    ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor =
        ArgumentCaptor.forClass(NotificationChannel.class);
    doReturn(notificationRulesMap).when(pmsEngineExpressionService).resolve(eq(ambiance), any(), eq(true));
    notificationHelper.sendNotification(ambiance, PipelineEventType.PIPELINE_SUCCESS, nodeExecution, 1L);
    verify(notificationClient, times(1)).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
    EmailChannel notificationChannel = (EmailChannel) notificationChannelArgumentCaptor.getValue();
    assertTrue(notificationChannel.getRecipients().contains("admin@harness.io"));
    assertTrue(notificationChannel.getRecipients().contains("test@harness.io"));
  }
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testWebhookNotification() {
    PlanNode planNode = PlanNode.builder().identifier("dummyIdentifier").build();
    NodeExecution nodeExecution =
        NodeExecution.builder().status(Status.SUCCEEDED).startTs(0L).ambiance(ambiance).build();
    when(planExecutionService.getPlanExecutionMetadata(anyString()))
        .thenReturn(PlanExecution.builder().status(Status.SUCCEEDED).startTs(0L).endTs(0L).build());
    when(planExecutionMetadataService.findByPlanExecutionId(anyString(), anyString()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().yaml(webhookNotificationYaml).build()));
    when(htmlInputSanitizer.sanitizeInput(any())).thenReturn("dummy");
    when(pipelineExpressionHelper.generateUrl(any(), any())).thenReturn(executionUrl);
    when(pipelineExpressionHelper.generatePipelineUrl(any(), any())).thenReturn("pipelineUrl");
    when(pmsExecutionService.fetchExecutionSummaryFromDb(any(), any()))
        .thenReturn(
            PipelineExecutionSummaryEntity.builder()
                .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                          .setTriggerType(TriggerType.MANUAL)
                                          .setTriggeredBy(TriggeredBy.newBuilder()
                                                              .setIdentifier("user")
                                                              .putExtraInfo("email", "user@harness.io")
                                                              .build())
                                          .build())
                .tags(Collections.singleton(NGTag.builder().key("<+pipeline.variables.testTag>").value("").build()))
                .build());
    ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor =
        ArgumentCaptor.forClass(NotificationChannel.class);
    doReturn(webhookNotificationRulesMap).when(pmsEngineExpressionService).resolve(eq(ambiance), any(), eq(true));

    notificationHelper.sendNotification(ambiance, PipelineEventType.PIPELINE_SUCCESS, nodeExecution, 1L);
    verify(notificationClient, times(1)).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
    verify(pmsEngineExpressionService, times(1))
        .resolve(ambiance,
            Collections.singletonList(NGTag.builder().key("<+pipeline.variables.testTag>").value("").build()),
            ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    WebhookChannel webhookChannel = (WebhookChannel) notificationChannelArgumentCaptor.getValue();
    assertThat(webhookChannel.getWebhookUrls().size()).isEqualTo(1);
    assertThat(webhookChannel.getWebhookUrls().get(0)).isEqualTo("https://www.google.com");
    assertThat(webhookChannel.getTemplateData().get("WEBHOOK_EVENT_DATA"))
        .isEqualTo("{\"accountIdentifier\":\"dummyAccount\",\"orgIdentifier\":\"dummyOrg\",\"projectIdentifier\":"
            + "\"dummyProject\",\"pipelineIdentifier\":\"dummyPipeline\",\"planExecutionId\":"
            + "\"dummyPlanExecutionId\",\"executionUrl\":\"http:127.0.0.1:8080/account/dummyAccount/cd/orgs/"
            + "dummyOrg/projects/dummyProject/pipelines/dummyPipeline/executions/dummyPlanExecutionId/"
            + "pipeline\",\"pipelineUrl\":\"pipelineUrl\",\"eventType\":\"PipelineSuccess\",\"nodeStatus\":"
            + "\"completed\",\"triggeredBy\":{\"triggerType\":\"MANUAL\",\"name\":\"user\",\"email\":\"user@"
            + "harness.io\"},\"startTime\":\"Thu Jan 01 00:00:00 UTC 1970\",\"startTs\":0,\"endTime\":\"Thu Jan "
            + "01 00:00:00 UTC 1970\",\"endTs\":0}");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testNotificationIsSentForAllEvents() {
    List<PipelineEventType> pipelineEventTypeList = new ArrayList<>();
    pipelineEventTypeList.add(PipelineEventType.PIPELINE_START);
    pipelineEventTypeList.add(PipelineEventType.PIPELINE_END);
    pipelineEventTypeList.add(PipelineEventType.PIPELINE_FAILED);
    pipelineEventTypeList.add(PipelineEventType.PIPELINE_PAUSED);
    pipelineEventTypeList.add(PipelineEventType.PIPELINE_SUCCESS);
    pipelineEventTypeList.add(PipelineEventType.STAGE_START);
    pipelineEventTypeList.add(STAGE_FAILED);
    pipelineEventTypeList.add(STAGE_SUCCESS);
    pipelineEventTypeList.add(PipelineEventType.STEP_FAILED);

    PlanNode planNode = PlanNode.builder().identifier("dummyIdentifier").build();
    NodeExecution nodeExecution =
        NodeExecution.builder().status(Status.SUCCEEDED).startTs(0L).ambiance(ambiance).build();
    when(planExecutionService.getPlanExecutionMetadata(anyString()))
        .thenReturn(PlanExecution.builder().status(Status.SUCCEEDED).startTs(0L).endTs(0L).build());
    when(planExecutionMetadataService.findByPlanExecutionId(anyString(), anyString()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().yaml(allEventsYaml).build()));
    doReturn(notificationRulesMap).when(pmsEngineExpressionService).resolve(eq(ambiance), any(), eq(true));
    when(htmlInputSanitizer.sanitizeInput(anyString())).thenReturn("dummy");
    when(pmsExecutionService.fetchExecutionSummaryFromDb(any(), any()))
        .thenReturn(PipelineExecutionSummaryEntity.builder()
                        .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                  .setTriggerType(TriggerType.MANUAL)
                                                  .setTriggeredBy(TriggeredBy.newBuilder()
                                                                      .setIdentifier("user")
                                                                      .putExtraInfo("email", "user@harness.io")
                                                                      .build())
                                                  .build())
                        .build());

    for (int idx = 0; idx < pipelineEventTypeList.size(); idx++) {
      notificationHelper.sendNotification(ambiance, pipelineEventTypeList.get(idx), nodeExecution, 1L);
      verify(notificationClient, times(idx + 1)).sendNotificationAsync(any());
    }
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testcreateNotificationRules() {
    NotificationRules notificationRules = notificationHelper.createNotificationRules(ambiance, pipelineEventType);
    assertThat(notificationRules.isEnabled()).isTrue();
    assertThat(notificationRules.getPipelineEvents().stream().findFirst().get().getType()).isEqualTo(pipelineEventType);
    assertThat(notificationRules.getNotificationChannelWrapper().getValue().getType())
        .isEqualTo(NotificationChannelType.EMAIL);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testSendNotificationOnlyToUserWhoTriggeredPipeline() {
    doNothing().when(notificationHelper).sendNotificationInternal(any(), any(), any());
    doReturn(Collections.EMPTY_MAP)
        .when(notificationHelper)
        .constructTemplateData(any(), any(), any(), any(), any(), any());
    notificationHelper.sendNotificationOnlyToUserWhoTriggeredPipeline(
        ambiance, pipelineEventType, null, null, true, null);
    verify(notificationHelper, times(1)).createNotificationRules(any(), any());
    verify(notificationHelper, times(1)).sendNotificationInternal(any(), any(), any());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetStageIdentifier() {
    String planExecutionId = generateUuid();
    PlanNode stagePlanNode = PlanNode.builder()
                                 .uuid(generateUuid())
                                 .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                 .identifier("dummyIdentifier_0")
                                 .build();
    PlanNode strategyPlanNode = PlanNode.builder()
                                    .uuid(generateUuid())
                                    .stepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                                    .identifier("dummyIdentifier")
                                    .build();
    Ambiance.Builder ambianceBuilder = Ambiance.newBuilder()
                                           .setPlanExecutionId(planExecutionId)
                                           .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagePlanNode));
    NodeExecutionBuilder nodeExecutionBuilder = NodeExecution.builder().ambiance(ambianceBuilder.build());
    assertEquals(notificationHelper.getStageIdentifier(nodeExecutionBuilder.build()), "dummyIdentifier_0");
    ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), strategyPlanNode));
    nodeExecutionBuilder.ambiance(ambianceBuilder.build());
    assertEquals(notificationHelper.getStageIdentifier(nodeExecutionBuilder.build()), "dummyIdentifier");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPipelineEvent() {
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .identifier("nodeIdentifier")
                                      .name("nodeName")
                                      .status(Status.SUCCEEDED)
                                      .startTs(0L)
                                      .ambiance(ambiance)
                                      .build();
    when(planExecutionService.getPlanExecutionMetadata(anyString()))
        .thenReturn(PlanExecution.builder().status(Status.SUCCEEDED).startTs(0L).endTs(0L).build());
    when(planExecutionMetadataService.findByPlanExecutionId(anyString(), anyString()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().yaml(allEventsYaml).build()));
    doReturn(notificationRulesMap).when(pmsEngineExpressionService).resolve(eq(ambiance), any(), eq(true));
    when(htmlInputSanitizer.sanitizeInput(anyString())).thenReturn("dummy");
    when(pmsExecutionService.fetchExecutionSummaryFromDb(any(), any()))
        .thenReturn(PipelineExecutionSummaryEntity.builder()
                        .name("Pipeline")
                        .orgIdentifier("org")
                        .pipelineIdentifier("pipelineIdentifier")
                        .projectIdentifier("proj")
                        .failureInfo(FailureInfoDTO.builder().message("Pipeline Failed").build())
                        .tags(Collections.singleton(NGTag.builder().key("k1").value("v1").build()))
                        .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                  .setTriggerType(TriggerType.MANUAL)
                                                  .setTriggeredBy(TriggeredBy.newBuilder()
                                                                      .setIdentifier("user")
                                                                      .putExtraInfo("email", "user@harness.io")
                                                                      .build())
                                                  .build())
                        .build());
    Map<String, String> notifyResponse = notificationHelper.constructTemplateData(
        ambiance, PipelineEventType.PIPELINE_FAILED, nodeExecution, updatedAt, "org", "proj");
    assertThat(notifyResponse).isNotEmpty();
    JSONObject json = new JSONObject(notifyResponse.get("WEBHOOK_EVENT_DATA"));
    assertThat(json.get("orgIdentifier")).isEqualTo("org");
    JSONArray jsonArray = json.getJSONArray("tag");
    jsonArray.get(0).toString();
    assertThat("{\"value\":\"v1\",\"key\":\"k1\"}").isEqualTo(jsonArray.get(0).toString());
    assertThat(json.get("pipelineName")).isEqualTo("Pipeline");
    assertThat(json.get("eventType")).isEqualTo("PipelineFailed");
    assertThat(json.get("errorMessage")).isEqualTo("Pipeline Failed");
    when(pmsExecutionService.fetchExecutionSummaryFromDb(any(), any()))
        .thenReturn(PipelineExecutionSummaryEntity.builder()
                        .name("Pipeline")
                        .orgIdentifier("org")
                        .pipelineIdentifier("pipelineIdentifier")
                        .projectIdentifier("proj")
                        .tags(Collections.singleton(NGTag.builder().key("k1").value("v1").build()))
                        .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                  .setTriggerType(TriggerType.MANUAL)
                                                  .setTriggeredBy(TriggeredBy.newBuilder()
                                                                      .setIdentifier("user")
                                                                      .putExtraInfo("email", "user@harness.io")
                                                                      .build())
                                                  .build())
                        .build());
    notifyResponse = notificationHelper.constructTemplateData(
        ambiance, PipelineEventType.PIPELINE_SUCCESS, null, updatedAt, "org", "proj");
    assertThat(notifyResponse).isNotEmpty();
    json = new JSONObject(notifyResponse.get("WEBHOOK_EVENT_DATA"));
    assertThat(json.get("orgIdentifier")).isEqualTo("org");
    jsonArray = json.getJSONArray("tag");
    jsonArray.get(0).toString();
    assertThat("{\"value\":\"v1\",\"key\":\"k1\"}").isEqualTo(jsonArray.get(0).toString());
    assertThat(json.get("pipelineName")).isEqualTo("Pipeline");
    assertThat(json.get("eventType")).isEqualTo("PipelineSuccess");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testStageEvent() {
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .identifier("nodeIdentifier")
            .name("nodeName")
            .status(Status.SUCCEEDED)
            .startTs(0L)
            .ambiance(ambiance)
            .failureInfo(FailureInfo.newBuilder()
                             .setErrorMessage("Shell Script execution failed. Please check execution logs.")
                             .build())
            .build();
    when(planExecutionService.getPlanExecutionMetadata(anyString()))
        .thenReturn(PlanExecution.builder().status(Status.SUCCEEDED).startTs(0L).endTs(0L).build());
    when(planExecutionMetadataService.findByPlanExecutionId(anyString(), anyString()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().yaml(allEventsYaml).build()));
    doReturn(notificationRulesMap).when(pmsEngineExpressionService).resolve(eq(ambiance), any(), eq(true));
    when(htmlInputSanitizer.sanitizeInput(anyString())).thenReturn("dummy");
    when(pmsExecutionService.fetchExecutionSummaryFromDb(any(), any()))
        .thenReturn(PipelineExecutionSummaryEntity.builder()
                        .name("Pipeline")
                        .orgIdentifier("org")
                        .pipelineIdentifier("pipelineIdentifier")
                        .projectIdentifier("proj")
                        .failureInfo(FailureInfoDTO.builder().message("Pipeline Failed").build())
                        .tags(Collections.singleton(NGTag.builder().key("k1").value("v1").build()))
                        .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                  .setTriggerType(TriggerType.MANUAL)
                                                  .setTriggeredBy(TriggeredBy.newBuilder()
                                                                      .setIdentifier("user")
                                                                      .putExtraInfo("email", "user@harness.io")
                                                                      .build())
                                                  .build())
                        .build());
    Map<String, String> notifyResponse = notificationHelper.constructTemplateData(
        ambiance, PipelineEventType.STAGE_FAILED, nodeExecution, updatedAt, "org", "proj");
    assertThat(notifyResponse).isNotEmpty();
    JSONObject json = new JSONObject(notifyResponse.get("WEBHOOK_EVENT_DATA"));
    assertThat(json.get("orgIdentifier")).isEqualTo("org");
    JSONArray jsonArray = json.getJSONArray("tag");
    jsonArray.get(0).toString();
    assertThat("{\"value\":\"v1\",\"key\":\"k1\"}").isEqualTo(jsonArray.get(0).toString());
    assertThat(json.get("pipelineName")).isEqualTo("Pipeline");
    assertThat(json.get("eventType")).isEqualTo("StageFailed");
    assertThat(json.get("stageName")).isEqualTo("nodeName");
    assertThat(json.get("errorMessage")).isEqualTo("Shell Script execution failed. Please check execution logs.");
    notifyResponse = notificationHelper.constructTemplateData(
        ambiance, PipelineEventType.STAGE_SUCCESS, nodeExecution, updatedAt, "org", "proj");
    assertThat(notifyResponse).isNotEmpty();
    json = new JSONObject(notifyResponse.get("WEBHOOK_EVENT_DATA"));
    assertThat(json.get("orgIdentifier")).isEqualTo("org");
    jsonArray = json.getJSONArray("tag");
    jsonArray.get(0).toString();
    assertThat("{\"value\":\"v1\",\"key\":\"k1\"}").isEqualTo(jsonArray.get(0).toString());
    assertThat(json.get("pipelineName")).isEqualTo("Pipeline");
    assertThat(json.get("eventType")).isEqualTo("StageSuccess");
    assertThat(json.get("stageName")).isEqualTo("nodeName");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testStepEvent() {
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .identifier("nodeIdentifier")
            .name("nodeName")
            .status(Status.SUCCEEDED)
            .startTs(0L)
            .ambiance(ambiance)
            .failureInfo(FailureInfo.newBuilder()
                             .setErrorMessage("Shell Script execution failed. Please check execution logs.")
                             .build())
            .build();
    when(planExecutionService.getPlanExecutionMetadata(anyString()))
        .thenReturn(PlanExecution.builder().status(Status.SUCCEEDED).startTs(0L).endTs(0L).build());
    when(planExecutionMetadataService.findByPlanExecutionId(anyString(), anyString()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().yaml(allEventsYaml).build()));
    doReturn(notificationRulesMap).when(pmsEngineExpressionService).resolve(eq(ambiance), any(), eq(true));
    when(htmlInputSanitizer.sanitizeInput(anyString())).thenReturn("dummy");
    when(pmsExecutionService.fetchExecutionSummaryFromDb(any(), any()))
        .thenReturn(PipelineExecutionSummaryEntity.builder()
                        .name("Pipeline")
                        .orgIdentifier("org")
                        .pipelineIdentifier("pipelineIdentifier")
                        .projectIdentifier("proj")
                        .failureInfo(FailureInfoDTO.builder().message("Pipeline Failed").build())
                        .tags(Collections.singleton(NGTag.builder().key("k1").value("v1").build()))
                        .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                  .setTriggerType(TriggerType.MANUAL)
                                                  .setTriggeredBy(TriggeredBy.newBuilder()
                                                                      .setIdentifier("user")
                                                                      .putExtraInfo("email", "user@harness.io")
                                                                      .build())
                                                  .build())
                        .build());
    Map<String, String> notifyResponse = notificationHelper.constructTemplateData(
        ambiance, PipelineEventType.STEP_FAILED, nodeExecution, updatedAt, "org", "proj");
    assertThat(notifyResponse).isNotEmpty();
    JSONObject json = new JSONObject(notifyResponse.get("WEBHOOK_EVENT_DATA"));
    assertThat(json.get("orgIdentifier")).isEqualTo("org");
    JSONArray jsonArray = json.getJSONArray("tag");
    jsonArray.get(0).toString();
    assertThat("{\"value\":\"v1\",\"key\":\"k1\"}").isEqualTo(jsonArray.get(0).toString());
    assertThat(json.get("pipelineName")).isEqualTo("Pipeline");
    assertThat(json.get("eventType")).isEqualTo("StepFailed");
    assertThat(json.get("stepName")).isEqualTo("nodeName");
    assertThat(json.get("errorMessage")).isEqualTo("Shell Script execution failed. Please check execution logs.");
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetErrorMessageForNotificationTemplate() {
    String expectedErrorMessage = "Step failed";
    String errorMessage = NotificationHelper.getErrorMessage(PipelineEventType.STEP_FAILED,
        FailureInfo.newBuilder().setErrorMessage(expectedErrorMessage).build(), null, true);
    assertEquals(errorMessage, expectedErrorMessage);

    errorMessage = NotificationHelper.getErrorMessage(PipelineEventType.STEP_FAILED,
        FailureInfo.newBuilder().setErrorMessage(expectedErrorMessage).build(), null, false);
    assertEquals(errorMessage, expectedErrorMessage);

    expectedErrorMessage = "Pipeline failed";
    errorMessage = NotificationHelper.getErrorMessage(PipelineEventType.PIPELINE_FAILED, null,
        PipelineExecutionSummaryEntity.builder()
            .failureInfo(FailureInfoDTO.builder().message(expectedErrorMessage).build())
            .build(),
        true);
    assertEquals(errorMessage, expectedErrorMessage);

    errorMessage = NotificationHelper.getErrorMessage(PipelineEventType.PIPELINE_FAILED, null,
        PipelineExecutionSummaryEntity.builder()
            .failureInfo(FailureInfoDTO.builder().message(expectedErrorMessage).build())
            .build(),
        false);
    assertEquals(errorMessage, expectedErrorMessage);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetErrorMessageForNotificationTemplateWithFailureInfo() {
    String expectedErrorMessage = "Step failed, Error Message3, Error Message2, Error Message1";
    FailureData failureData1 = FailureData.newBuilder().setMessage("Error Message1").build();
    FailureData failureData2 = FailureData.newBuilder().setMessage("Error Message2").build();
    FailureData failureData3 = FailureData.newBuilder().setMessage("Error Message3").build();
    FailureData failureData4 = FailureData.newBuilder().setMessage("Step failed").build();
    String errorMessage = NotificationHelper.getErrorMessage(PipelineEventType.STEP_FAILED,
        FailureInfo.newBuilder()
            .setErrorMessage("Step failed")
            .addFailureData(failureData1)
            .addFailureData(failureData2)
            .addFailureData(failureData3)
            .addFailureData(failureData4)
            .build(),
        null, true);
    assertEquals(errorMessage, expectedErrorMessage);

    errorMessage = NotificationHelper.getErrorMessage(PipelineEventType.STEP_FAILED,
        FailureInfo.newBuilder()
            .setErrorMessage("Step failed")
            .addFailureData(failureData1)
            .addFailureData(failureData2)
            .addFailureData(failureData3)
            .addFailureData(failureData4)
            .build(),
        null, false);
    assertEquals(errorMessage, expectedErrorMessage);

    // Null Case
    errorMessage = NotificationHelper.getErrorMessage(
        PipelineEventType.STEP_FAILED, FailureInfo.newBuilder().setErrorMessage("Step failed").build(), null, false);
    assertEquals(errorMessage, "Step failed");

    expectedErrorMessage = "Pipeline failed, message3, message2, message1";
    List<ResponseMessage> messages = new ArrayList<>();
    messages.add(ResponseMessage.builder().message("message1").build());
    messages.add(ResponseMessage.builder().message("message2").build());
    messages.add(ResponseMessage.builder().message("message3").build());
    messages.add(ResponseMessage.builder().message("Pipeline failed").build());
    errorMessage = NotificationHelper.getErrorMessage(PipelineEventType.PIPELINE_FAILED, null,
        PipelineExecutionSummaryEntity.builder()
            .failureInfo(FailureInfoDTO.builder().message("Pipeline failed").responseMessages(messages).build())
            .build(),
        true);
    assertEquals(errorMessage, expectedErrorMessage);

    errorMessage = NotificationHelper.getErrorMessage(PipelineEventType.PIPELINE_FAILED, null,
        PipelineExecutionSummaryEntity.builder()
            .failureInfo(FailureInfoDTO.builder().message("Pipeline failed").responseMessages(messages).build())
            .build(),
        false);
    assertEquals(errorMessage, expectedErrorMessage);

    // Null Cases
    errorMessage = NotificationHelper.getErrorMessage(PipelineEventType.PIPELINE_FAILED, null,
        PipelineExecutionSummaryEntity.builder()
            .failureInfo(FailureInfoDTO.builder().message("Pipeline failed").responseMessages(null).build())
            .build(),
        false);
    assertEquals(errorMessage, "Pipeline failed");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSendNotificationInternal() {
    PipelineEventType pipelineStartEventType = PipelineEventType.PIPELINE_START;
    ArgumentCaptor<NotificationTriggerRequest> argumentCaptor =
        ArgumentCaptor.forClass(NotificationTriggerRequest.class);
    notificationHelper.sendCentralisedNotification(pipelineStartEventType, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
        PROJECT_IDENTIFIER, new HashMap<>(), "pipelineIdentifier", null);
    verify(notificationClient, times(1)).sendNotificationTrigger(argumentCaptor.capture());
    NotificationTriggerRequest notificationTriggerRequest = argumentCaptor.getValue();
    assertEquals(notificationTriggerRequest.getTemplateDataMap().get("TEMPLATE_IDENTIFIER"), "pms_pipeline");

    pipelineStartEventType = PipelineEventType.STAGE_START;
    notificationHelper.sendCentralisedNotification(pipelineStartEventType, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
        PROJECT_IDENTIFIER, new HashMap<>(), "pipelineIdentifier", null);
    verify(notificationClient, times(2)).sendNotificationTrigger(argumentCaptor.capture());
    notificationTriggerRequest = argumentCaptor.getValue();
    assertEquals(notificationTriggerRequest.getTemplateDataMap().get("TEMPLATE_IDENTIFIER"), "pms_stage");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testSendNotificationInternalCustomNotificationTemplate() {
    Map<String, String> notificationContent = new HashMap<>();
    notificationContent.put("key1", "value1");
    ArrayList<NotificationRules> notificationRules = new ArrayList();
    List<PipelineEvent> pipelineEvents = new ArrayList<>();
    pipelineEvents.add(PipelineEvent.builder().type(ALL_EVENTS).build());
    NotificationChannelWrapper notificationChannelWrapper =
        NotificationChannelWrapper.builder()
            .type("Webhook")
            .notificationChannel(PmsWebhookChannel.builder()
                                     .webhookUrl(ParameterField.<String>builder().value("https://abc.com").build())
                                     .build())
            .build();
    notificationRules.add(
        NotificationRules.builder()
            .name("test")
            .enabled(true)
            .pipelineEvents(pipelineEvents)
            .notificationChannelWrapper(
                ParameterField.<NotificationChannelWrapper>builder().value(notificationChannelWrapper).build())
            .build());

    String yaml = "pipeline:\n"
        + "    identifier: testnotify\n"
        + "    name: testnotify\n"
        + "    projectIdentifier: Test_Project\n"
        + "    orgIdentifier: default\n"
        + "    tags: {}\n"
        + "    stages:\n"
        + "        - stage:\n"
        + "            identifier: c1\n"
        + "            type: Custom\n"
        + "            name: c1\n"
        + "            description: \"\"\n"
        + "            spec:\n"
        + "                execution:\n"
        + "                    steps:\n"
        + "                        - step:\n"
        + "                            identifier: ShellScript_1\n"
        + "                            type: ShellScript\n"
        + "                            name: ShellScript_1\n"
        + "                            spec:\n"
        + "                                shell: Bash\n"
        + "                                executionTarget: {}\n"
        + "                                source:\n"
        + "                                    type: Inline\n"
        + "                                    spec:\n"
        + "                                        script: echo 1\n"
        + "                                environmentVariables: []\n"
        + "                                outputVariables: []\n"
        + "                            timeout: 10m\n"
        + "            tags: {}\n"
        + "    notificationRules:\n"
        + "        - identifier: test\n"
        + "          name: test\n"
        + "          pipelineEvents:\n"
        + "              - type: AllEvents\n"
        + "          template:\n"
        + "              templateRef: account.testJpmc\n"
        + "          notificationMethod:\n"
        + "              type: Webhook\n"
        + "              spec:\n"
        + "                  webhookUrl: http://abc.com\n"
        + "          enabled: true\n";

    TemplateMergeResponseDTO mergeResponse =
        TemplateMergeResponseDTO.builder()
            .mergedPipelineYaml("pipeline:\n  notificationRules:\n    body:\n      content: \"test content\"")
            .build();
    when(pipelineTemplateHelper.resolveTemplateRefsInPipeline(
             any(), any(), any(), anyString(), eq(true), eq(false), any(), any(), eq(true)))
        .thenReturn(mergeResponse);
    NotificationContext notificationContext = NotificationContext.builder()
                                                  .notificationContent(notificationContent)
                                                  .yaml(yaml)
                                                  .pipelineEventType(PIPELINE_START)
                                                  .notificationRulesList(notificationRules)
                                                  .build();
    notificationHelper.sendNotificationInternal(notificationContext, ambiance, null);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testStageDetailsIncludedForStageEvents() {
    // Setup test data
    String stageDetails = "| serviceName | envName | infraName |";
    CDStageSummaryResponseDTO response =
        CDStageSummaryResponseDTO.builder().environment("envName").service("serviceName").infra("infraName").build();
    Map<String, CDStageSummaryResponseDTO> executionSummary = new HashMap<>();
    executionSummary.put("dummyStageId", response);
    Map<String, CDStageSummaryResponseDTO> planSummary = new HashMap<>();
    planSummary.put("dummyIdentifier", response);
    when(stageMetadataNotificationHelper.getCdFinishedFormattedSummary(any(), any())).thenReturn(executionSummary);
    when(stageMetadataNotificationHelper.getCdStagePlanCreationFormattedSummary(any(), any(), any()))
        .thenReturn(planSummary);

    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().yaml(yaml).build();
    NodeExecution nodeExecution =
        NodeExecution.builder().status(Status.SUCCEEDED).startTs(0L).ambiance(ambiance).build();
    when(planExecutionMetadataService.findByPlanExecutionId(any(), any()))
        .thenReturn(java.util.Optional.ofNullable(planExecutionMetadata));
    doReturn(null).when(notificationClient).sendNotificationAsync(any());
    when(planExecutionService.getPlanExecutionMetadata(anyString()))
        .thenReturn(PlanExecution.builder().status(Status.SUCCEEDED).startTs(0L).endTs(0L).build());
    doReturn(executionUrl)
        .when(notificationHelper)
        .generateUrl(any(Ambiance.class), any(PipelineExecutionSummaryEntity.class));
    when(pmsExecutionService.fetchExecutionSummaryFromDb(any(), any()))
        .thenReturn(PipelineExecutionSummaryEntity.builder()
                        .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                  .setTriggerType(TriggerType.MANUAL)
                                                  .setTriggeredBy(TriggeredBy.newBuilder()
                                                                      .setIdentifier("user")
                                                                      .putExtraInfo("email", "user@harness.io")
                                                                      .build())
                                                  .build())
                        .build());
    doReturn("serviceName").when(pmsEngineExpressionService).renderExpression(eq(ambiance), eq("serviceName"));
    doReturn("envName").when(pmsEngineExpressionService).renderExpression(eq(ambiance), eq("envName"));
    doReturn("infraName").when(pmsEngineExpressionService).renderExpression(eq(ambiance), eq("infraName"));
    String enriched = PipelineEventType.STAGE_SUCCESS.getDisplayName() + " " + stageDetails;

    // FF off: EVENT_TYPE contains enriched string (backward compat); EVENT_DETAILS always enriched
    Map<String, String> templateData = notificationHelper.constructTemplateData(
        ambiance, PipelineEventType.STAGE_SUCCESS, nodeExecution, 0L, "org", "proj");
    assertThat(templateData.get(NotificationConstants.EVENT_TYPE)).isEqualTo(enriched);
    assertThat(templateData.get(NotificationConstants.EVENT_DETAILS)).isEqualTo(enriched);

    // FF on: EVENT_TYPE is clean; EVENT_DETAILS still enriched
    when(pmsFeatureFlagHelper.isEnabled(any(), eq(FeatureName.CDS_CLEAN_NOTIFICATION_EVENT_TYPE))).thenReturn(true);
    templateData = notificationHelper.constructTemplateData(
        ambiance, PipelineEventType.STAGE_SUCCESS, nodeExecution, 0L, "org", "proj");
    assertThat(templateData.get(NotificationConstants.EVENT_TYPE))
        .isEqualTo(PipelineEventType.STAGE_SUCCESS.getDisplayName());
    assertThat(templateData.get(NotificationConstants.EVENT_DETAILS)).isEqualTo(enriched);
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testStageNoDetailsIncludedForStageEvents() {
    // build an empty CDStageSummaryResponseDTO to simulate stages without environment or service
    CDStageSummaryResponseDTO response = CDStageSummaryResponseDTO.builder().build();
    Map<String, CDStageSummaryResponseDTO> executionSummary = new HashMap<>();
    executionSummary.put(ambiance.getStageExecutionId(), response);
    // simulates stages that have no svc env or infra information
    when(stageMetadataNotificationHelper.getCdStagePlanCreationFormattedSummary(any(), any(), any()))
        .thenReturn(executionSummary);

    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().yaml(yaml).build();
    NodeExecution nodeExecution =
        NodeExecution.builder().status(Status.SUCCEEDED).startTs(0L).ambiance(ambiance).build();
    when(planExecutionMetadataService.findByPlanExecutionId(any(), any()))
        .thenReturn(java.util.Optional.ofNullable(planExecutionMetadata));
    doReturn(null).when(notificationClient).sendNotificationAsync(any());
    when(planExecutionService.getPlanExecutionMetadata(anyString()))
        .thenReturn(PlanExecution.builder().status(Status.SUCCEEDED).startTs(0L).endTs(0L).build());
    doReturn(executionUrl)
        .when(notificationHelper)
        .generateUrl(any(Ambiance.class), any(PipelineExecutionSummaryEntity.class));
    when(pmsExecutionService.fetchExecutionSummaryFromDb(any(), any()))
        .thenReturn(PipelineExecutionSummaryEntity.builder()
                        .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                  .setTriggerType(TriggerType.MANUAL)
                                                  .setTriggeredBy(TriggeredBy.newBuilder()
                                                                      .setIdentifier("user")
                                                                      .putExtraInfo("email", "user@harness.io")
                                                                      .build())
                                                  .build())
                        .build());

    // No infra details: both EVENT_TYPE and EVENT_DETAILS equal the clean event name
    Map<String, String> templateData = notificationHelper.constructTemplateData(
        ambiance, PipelineEventType.STAGE_SUCCESS, nodeExecution, 0L, "org", "proj");
    String cleanName = PipelineEventType.STAGE_SUCCESS.getDisplayName();
    assertThat(templateData.get(NotificationConstants.EVENT_TYPE)).isEqualTo(cleanName);
    assertThat(templateData.get(NotificationConstants.EVENT_DETAILS)).isEqualTo(cleanName);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testValidateNotificationRulesWithUnresolvedInputs() {
    Boolean isInvalid = notificationHelper.validateNotificationRulesWithUnresolvedInputs(notificationYaml);
    assertThat(isInvalid).isNotNull();
    assertThat(isInvalid).isEqualTo(true);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListNotificationRulesWithUnresolvedInputs() {
    ArrayList<Map<String, String>> response =
        notificationHelper.listNotificationRulesWithUnresolvedInputs(notificationYaml);
    assertThat(response).isNotNull();
    assertThat(response.get(0).get("notificationIdentifier")).isEqualTo("rule1");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testSendNotificationInternal_WithNotificationBodyTemplate_ShouldResolveDirectNotificationBody() {
    Map<String, String> notificationContent = new HashMap<>();
    notificationContent.put("key1", "value1");

    ArrayList<NotificationRules> notificationRules = new ArrayList<>();
    List<PipelineEvent> pipelineEvents = new ArrayList<>();
    pipelineEvents.add(PipelineEvent.builder().type(ALL_EVENTS).build());

    NotificationChannelWrapper notificationChannelWrapper =
        NotificationChannelWrapper.builder()
            .type("Webhook")
            .notificationChannel(PmsWebhookChannel.builder()
                                     .webhookUrl(ParameterField.<String>builder().value("https://abc.com").build())
                                     .build())
            .build();

    notificationRules.add(
        NotificationRules.builder()
            .name("test")
            .enabled(true)
            .pipelineEvents(pipelineEvents)
            .notificationChannelWrapper(
                ParameterField.<NotificationChannelWrapper>builder().value(notificationChannelWrapper).build())
            .build());

    // YAML with body content that will create the notificationBody structure
    String yaml = "pipeline:\n"
        + "    identifier: testnotify\n"
        + "    name: testnotify\n"
        + "    projectIdentifier: Test_Project\n"
        + "    orgIdentifier: default\n"
        + "    notificationRules:\n"
        + "        - identifier: test\n"
        + "          name: test\n"
        + "          pipelineEvents:\n"
        + "              - type: AllEvents\n"
        + "          body:\n"
        + "              content: Custom notification content\n"
        + "          notificationMethod:\n"
        + "              type: Webhook\n"
        + "              spec:\n"
        + "                  webhookUrl: http://abc.com\n"
        + "          enabled: true\n";
    NotificationContext notificationContext = NotificationContext.builder()
                                                  .notificationContent(notificationContent)
                                                  .yaml(yaml)
                                                  .pipelineEventType(PIPELINE_START)
                                                  .notificationRulesList(notificationRules)
                                                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJECT_IDENTIFIER)
                                                  .build();

    notificationHelper.sendNotificationInternal(notificationContext, ambiance, null);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreateContextMapForResolution_ContainsResolveObjectsViaJsonSelect_WhenFFEnabled() throws Exception {
    Method method = NotificationHelper.class.getDeclaredMethod(
        "createContextMapForResolution", String.class, PipelineEventType.class, Map.class);
    method.setAccessible(true);

    when(pmsFeatureFlagHelper.isEnabled("testAccount", FeatureName.CDS_RESOLVE_OBJECTS_VIA_JSON_SELECT))
        .thenReturn(true);

    Map<String, String> notificationMapData = new HashMap<>();
    notificationMapData.put("nodeStartDate", "2024-01-01");
    notificationMapData.put("duration", "5m");

    Map<String, Object> contextMap = (Map<String, Object>) method.invoke(
        notificationHelper, "testAccount", PipelineEventType.PIPELINE_SUCCESS, notificationMapData);

    assertThat(contextMap).containsKey(JsonConstants.RESOLVE_OBJECTS_VIA_JSON_SELECT);
    assertThat(contextMap.get(JsonConstants.RESOLVE_OBJECTS_VIA_JSON_SELECT)).isEqualTo("true");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCustomNotificationTemplate_JsonSelectContextPassedToExpressionResolver_WhenFFEnabled() {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_IDENTIFIER, FeatureName.CDS_RESOLVE_OBJECTS_VIA_JSON_SELECT))
        .thenReturn(true);
    Map<String, String> notificationContent = new HashMap<>();
    notificationContent.put("key1", "value1");

    ArrayList<NotificationRules> notificationRules = new ArrayList<>();
    List<PipelineEvent> pipelineEvents = new ArrayList<>();
    pipelineEvents.add(PipelineEvent.builder().type(ALL_EVENTS).build());

    NotificationChannelWrapper notificationChannelWrapper =
        NotificationChannelWrapper.builder()
            .type("Webhook")
            .notificationChannel(PmsWebhookChannel.builder()
                                     .webhookUrl(ParameterField.<String>builder().value("https://abc.com").build())
                                     .build())
            .build();
    notificationRules.add(
        NotificationRules.builder()
            .name("test")
            .enabled(true)
            .pipelineEvents(pipelineEvents)
            .notificationChannelWrapper(
                ParameterField.<NotificationChannelWrapper>builder().value(notificationChannelWrapper).build())
            .build());

    String yaml = "pipeline:\n"
        + "    identifier: testnotify\n"
        + "    name: testnotify\n"
        + "    notificationRules:\n"
        + "        - identifier: test\n"
        + "          name: test\n"
        + "          pipelineEvents:\n"
        + "              - type: AllEvents\n"
        + "          template:\n"
        + "              templateRef: account.testTemplate\n"
        + "          notificationMethod:\n"
        + "              type: Webhook\n"
        + "              spec:\n"
        + "                  webhookUrl: http://abc.com\n"
        + "          enabled: true\n";

    TemplateMergeResponseDTO mergeResponse =
        TemplateMergeResponseDTO.builder()
            .mergedPipelineYaml("pipeline:\n  notificationRules:\n    body:\n      content: \"resolved\"")
            .build();
    when(pipelineTemplateHelper.resolveTemplateRefsInPipeline(
             anyString(), anyString(), anyString(), anyString(), eq(true), eq(false), any(), any(), eq(true)))
        .thenReturn(mergeResponse);

    ArgumentCaptor<Map> contextMapCaptor = ArgumentCaptor.forClass(Map.class);
    when(pmsEngineExpressionService.resolve(
             any(Ambiance.class), anyString(), any(ExpressionMode.class), contextMapCaptor.capture()))
        .thenReturn("resolved content");

    NotificationContext notificationContext = NotificationContext.builder()
                                                  .notificationContent(notificationContent)
                                                  .yaml(yaml)
                                                  .pipelineEventType(PIPELINE_SUCCESS)
                                                  .notificationRulesList(notificationRules)
                                                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJECT_IDENTIFIER)
                                                  .build();

    notificationHelper.sendNotificationInternal(notificationContext, ambiance, null);

    Map<String, Object> capturedContextMap = contextMapCaptor.getValue();
    assertThat(capturedContextMap).containsKey(JsonConstants.RESOLVE_OBJECTS_VIA_JSON_SELECT);
    assertThat(capturedContextMap.get(JsonConstants.RESOLVE_OBJECTS_VIA_JSON_SELECT)).isEqualTo("true");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testSendCNSNotification_Success() throws Exception {
    // Setup
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid("nodeExecutionId")
                                      .nodeId("nodeId")
                                      .ambiance(ambiance)
                                      .status(Status.SUCCEEDED)
                                      .build();

    // Create a real mock of AcquiredLock
    AcquiredLock lock = mock(AcquiredLock.class);
    doNothing().when(lock).close();

    // Mock the lock acquisition to return a non-null value (indicating lock was acquired)
    when(persistentLocker.waitToAcquireLock(anyString(), any(Duration.class), any(Duration.class))).thenReturn(lock);

    when(
        notificationEventsHelper.isNotificationEventAlreadySent(anyString(), anyString(), any(PipelineEventType.class)))
        .thenReturn(false);

    // Use doCallRealMethod() to call the actual implementation of the method under test
    doCallRealMethod()
        .when(notificationHelper)
        .sendNotification(
            any(Ambiance.class), any(PipelineEventType.class), any(NodeExecution.class), any(), anyLong());

    // Mock the plan execution metadata
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().pipelineYaml(yaml).build();
    when(planExecutionMetadataService.findByPlanExecutionId(anyString(), anyString()))
        .thenReturn(Optional.of(planExecutionMetadata));

    doNothing()
        .when(notificationHelper)
        .sendNotification(any(Ambiance.class), any(PipelineEventType.class), any(NodeExecution.class),
            any(PlanExecutionMetadata.class), anyLong());
    when(pmsExecutionService.fetchExecutionSummaryFromDb(any(), any()))
        .thenReturn(PipelineExecutionSummaryEntity.builder()
                        .pipelineIdentifier("pipelineIdentifier")
                        .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                  .setTriggerType(TriggerType.MANUAL)
                                                  .setTriggeredBy(TriggeredBy.newBuilder()
                                                                      .setIdentifier("user")
                                                                      .putExtraInfo("email", "user@harness.io")
                                                                      .build())
                                                  .build())
                        .build());
    when(planExecutionService.getPlanExecutionMetadata(anyString()))
        .thenReturn(PlanExecution.builder().status(Status.SUCCEEDED).startTs(0L).endTs(0L).build());

    // Execute
    notificationHelper.sendCNSNotification(ambiance, PIPELINE_START, nodeExecution, System.currentTimeMillis());

    // Verify
    verify(notificationEventsHelper, times(1))
        .isNotificationEventAlreadySent(anyString(), anyString(), eq(PIPELINE_START));
    verify(notificationHelper, times(1))
        .sendCentralisedNotification(eq(PIPELINE_START), anyString(), anyString(), anyString(), any(), any(), any());
    verify(persistentLocker, times(1)).waitToAcquireLock(anyString(), any(Duration.class), any(Duration.class));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testSendCNSNotification_WaitingForUserAction_SendsCentralisedNotification() throws Exception {
    // Setup – verifies WAITING_FOR_USER_ACTION is mapped and sent via CNS
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid("nodeExecutionId")
                                      .nodeId("nodeId")
                                      .ambiance(ambiance)
                                      .status(Status.SUCCEEDED)
                                      .build();

    AcquiredLock lock = mock(AcquiredLock.class);
    doNothing().when(lock).close();
    when(persistentLocker.waitToAcquireLock(anyString(), any(Duration.class), any(Duration.class))).thenReturn(lock);
    when(
        notificationEventsHelper.isNotificationEventAlreadySent(anyString(), anyString(), any(PipelineEventType.class)))
        .thenReturn(false);
    doCallRealMethod()
        .when(notificationHelper)
        .sendNotification(
            any(Ambiance.class), any(PipelineEventType.class), any(NodeExecution.class), any(), anyLong());
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().pipelineYaml(yaml).build();
    when(planExecutionMetadataService.findByPlanExecutionId(anyString(), anyString()))
        .thenReturn(Optional.of(planExecutionMetadata));
    doNothing()
        .when(notificationHelper)
        .sendNotification(any(Ambiance.class), any(PipelineEventType.class), any(NodeExecution.class),
            any(PlanExecutionMetadata.class), anyLong());
    when(pmsExecutionService.fetchExecutionSummaryFromDb(any(), any()))
        .thenReturn(PipelineExecutionSummaryEntity.builder()
                        .pipelineIdentifier("pipelineIdentifier")
                        .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                  .setTriggerType(TriggerType.MANUAL)
                                                  .setTriggeredBy(TriggeredBy.newBuilder()
                                                                      .setIdentifier("user")
                                                                      .putExtraInfo("email", "user@harness.io")
                                                                      .build())
                                                  .build())
                        .build());
    when(planExecutionService.getPlanExecutionMetadata(anyString()))
        .thenReturn(PlanExecution.builder().status(Status.SUCCEEDED).startTs(0L).endTs(0L).build());

    notificationHelper.sendCNSNotification(
        ambiance, WAITING_FOR_USER_ACTION, nodeExecution, System.currentTimeMillis());

    verify(notificationEventsHelper, times(1))
        .isNotificationEventAlreadySent(anyString(), anyString(), eq(WAITING_FOR_USER_ACTION));
    verify(notificationHelper, times(1))
        .sendCentralisedNotification(
            eq(WAITING_FOR_USER_ACTION), anyString(), anyString(), anyString(), any(), any(), any());
    verify(persistentLocker, times(1)).waitToAcquireLock(anyString(), any(Duration.class), any(Duration.class));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testSendCNSNotification_WhenNotificationAlreadySent_ShouldNotSendAgain() {
    // Setup
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid("nodeExecutionId")
                                      .nodeId("nodeId")
                                      .ambiance(ambiance)
                                      .status(Status.SUCCEEDED)
                                      .build();

    when(notificationHelper.notificationEventsHelper.isNotificationEventAlreadySent(
             anyString(), anyString(), any(PipelineEventType.class)))
        .thenReturn(true);
    notificationHelper.sendCNSNotification(ambiance, PIPELINE_START, nodeExecution, System.currentTimeMillis());
    verify(notificationHelper, never())
        .sendNotification(
            any(Ambiance.class), any(PipelineEventType.class), any(NodeExecution.class), any(), anyLong());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testSendCNSNotification_WithException_ShouldHandleGracefully() {
    // Setup
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid("nodeExecutionId")
                                      .nodeId("nodeId")
                                      .ambiance(ambiance)
                                      .status(Status.SUCCEEDED)
                                      .build();

    when(notificationHelper.notificationEventsHelper.isNotificationEventAlreadySent(
             anyString(), anyString(), any(PipelineEventType.class)))
        .thenThrow(new RuntimeException("Test Exception"));
    notificationHelper.sendCNSNotification(ambiance, PIPELINE_START, nodeExecution, System.currentTimeMillis());
    verify(notificationHelper, never())
        .sendNotification(
            any(Ambiance.class), any(PipelineEventType.class), any(NodeExecution.class), any(), anyLong());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testSendNotificationInternal_WithTabCharactersInErrorMessage_ShouldSucceed() {
    // Setup: Create a YAML with custom notification template
    String notificationYamlWithTemplate = "pipeline:\n"
        + "    name: TestPipeline\n"
        + "    identifier: TestPipeline\n"
        + "    notificationRules:\n"
        + "        - name: TestRule\n"
        + "          pipelineEvents:\n"
        + "              - type: PipelineFailed\n"
        + "          notificationMethod:\n"
        + "              type: Email\n"
        + "              spec:\n"
        + "                  userGroups: []\n"
        + "                  recipients: \n"
        + "                    - test@harness.io \n"
        + "          enabled: true\n"
        + "          body:\n"
        + "              content: \"Error: <+notification.errorMessage>\"\n"
        + "              variables:\n"
        + "                - name: testVar\n"
        + "                  value: testValue\n";

    // Mock: Expression resolution returns content with TAB character
    String resolvedBodyWithTab = "body:\n"
        + "  content: \"Error: Failed\twith\ttabs\"\n" // TAB characters in error message
        + "  notificationContent: \"Error: Failed\twith\ttabs\"\n";

    Map<String, String> notificationContent = new HashMap<>();
    notificationContent.put("ERROR_MESSAGE", "Failed\twith\ttabs");

    NotificationRules notificationRules =
        NotificationRules.builder()
            .name("TestRule")
            .enabled(true)
            .pipelineEvents(
                Collections.singletonList(PipelineEvent.builder().type(PipelineEventType.PIPELINE_FAILED).build()))
            .notificationChannelWrapper(ParameterField.createValueField(
                NotificationChannelWrapper.builder()
                    .type(NotificationChannelType.EMAIL)
                    .notificationChannel(io.harness.notification.channelDetails.PmsEmailChannel.builder()
                                             .recipients(Collections.singletonList("test@harness.io"))
                                             .userGroups(Collections.emptyList())
                                             .build())
                    .build()))
            .build();

    NotificationContext notificationContext = NotificationContext.builder()
                                                  .notificationRulesList(Collections.singletonList(notificationRules))
                                                  .pipelineEventType(PipelineEventType.PIPELINE_FAILED)
                                                  .identifier("TestRule")
                                                  .accountIdentifier("testAccount")
                                                  .orgIdentifier("testOrg")
                                                  .projectIdentifier("testProject")
                                                  .yaml(notificationYamlWithTemplate)
                                                  .notificationContent(notificationContent)
                                                  .build();

    when(pmsEngineExpressionService.resolve(any(Ambiance.class), anyString(), any(ExpressionMode.class), any()))
        .thenReturn(resolvedBodyWithTab);

    ArgumentCaptor<NotificationChannel> notificationChannelCaptor = ArgumentCaptor.forClass(NotificationChannel.class);
    doReturn(null).when(notificationClient).sendNotificationAsync(notificationChannelCaptor.capture());

    // Execute: This should not throw any exception even with TAB characters
    assertThatCode(() -> notificationHelper.sendNotificationInternal(notificationContext, ambiance, null))
        .doesNotThrowAnyException();

    // Verify: Notification was sent successfully and TABs were sanitized via YamlPipelineUtils.sanitiseYaml()
    verify(notificationClient, times(1)).sendNotificationAsync(any());

    // Verify that the resolved content in template data doesn't contain tabs (they were replaced with spaces)
    NotificationChannel capturedChannel = notificationChannelCaptor.getValue();
    String resolvedContent = capturedChannel.getTemplateData().get("resolvedNotificationContent");
    if (resolvedContent != null) {
      assertThat(resolvedContent).doesNotContain("\t");
      assertThat(resolvedContent).contains("  "); // Tabs replaced with two spaces
    }
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testProcessNotificationTemplateResolution_V0FlowWithTemplate_ShouldNotConvert() throws Exception {
    // Given: V0 pipeline with notification template
    String v0NotificationYaml = "pipeline:\n"
        + "  name: TestPipeline\n"
        + "  identifier: TestPipeline\n"
        + "  notificationRules:\n"
        + "    - name: rule1\n"
        + "      template:\n"
        + "        templateRef: account.notificationTemplate\n"
        + "      pipelineEvents:\n"
        + "        - type: PipelineSuccess\n"
        + "      notificationMethod:\n"
        + "        type: Email\n"
        + "        spec:\n"
        + "          recipients:\n"
        + "            - test@harness.io\n"
        + "      enabled: true\n";

    Ambiance v0Ambiance = Ambiance.newBuilder()
                              .putSetupAbstractions("accountId", ACCOUNT_IDENTIFIER)
                              .putSetupAbstractions("orgIdentifier", ORG_IDENTIFIER)
                              .putSetupAbstractions("projectIdentifier", PROJECT_IDENTIFIER)
                              .setMetadata(ExecutionMetadata.newBuilder()
                                               .setHarnessVersion(HarnessYamlVersion.V0) // V0 flow
                                               .setPipelineIdentifier("TestPipeline")
                                               .build())
                              .setPlanExecutionId("planExecId")
                              .build();

    when(planExecutionMetadataService.findByPlanExecutionId(anyString(), anyString()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().yaml(v0NotificationYaml).build()));

    // When: Processing notification template resolution
    notificationHelper.sendNotificationInternal(NotificationContext.builder()
                                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                    .orgIdentifier(ORG_IDENTIFIER)
                                                    .projectIdentifier(PROJECT_IDENTIFIER)
                                                    .yaml(v0NotificationYaml)
                                                    .build(),
        v0Ambiance, null);

    // Then: Should NOT call conversion method (v0 templates are used as-is)
    verify(notificationRulesMapper, never()).convertTemplateNodeV1ToV0(any());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testProcessNotificationTemplateResolution_V1FlowWithTemplate_ShouldConvert() throws Exception {
    String v1NotificationYaml = "pipeline:\n"
        + "  notifications:\n"
        + "    - name: rule1\n"
        + "      on:\n"
        + "        - pipeline: all\n"
        + "      uses: webhook\n"
        + "      with:\n"
        + "        url: https://abc.com\n"
        + "      template:\n"
        + "        with: account.notificationTemplate\n"
        + "        uses:\n"
        + "          versionLabel: v1\n"
        + "      enabled: true\n";
    NotificationRules rules =
        NotificationRules.builder()
            .name("rule1")
            .enabled(true)
            .notificationChannelWrapper(ParameterField.createValueField(
                NotificationChannelWrapper.builder()
                    .type(NotificationChannelType.WEBHOOK)
                    .notificationChannel(PmsWebhookChannel.builder()
                                             .webhookUrl(ParameterField.createValueField("https://abc.com"))
                                             .build())
                    .build()))
            .pipelineEvents(List.of(PipelineEvent.builder().type(PIPELINE_START).build(),
                PipelineEvent.builder().type(PIPELINE_SUCCESS).build(),
                PipelineEvent.builder().type(PIPELINE_FAILED).build()))
            .build();

    Ambiance v1Ambiance = Ambiance.newBuilder()
                              .putSetupAbstractions("accountId", ACCOUNT_IDENTIFIER)
                              .putSetupAbstractions("orgIdentifier", ORG_IDENTIFIER)
                              .putSetupAbstractions("projectIdentifier", PROJECT_IDENTIFIER)
                              .setMetadata(ExecutionMetadata.newBuilder()
                                               .setHarnessVersion(HarnessYamlVersion.V1) // V1 flow
                                               .setPipelineIdentifier("TestPipeline")
                                               .build())
                              .setPlanExecutionId("planExecId")
                              .build();

    io.harness.template.yaml.TemplateLinkConfig v0Template = new io.harness.template.yaml.TemplateLinkConfig();
    v0Template.setTemplateRef("account.notificationTemplate");

    when(planExecutionMetadataService.findByPlanExecutionId(anyString(), anyString()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().yaml(v1NotificationYaml).build()));
    when(notificationRulesMapper.convertTemplateNodeV1ToV0(any())).thenReturn(v0Template);

    // Mock template resolution to return a valid merged YAML
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder()
            .mergedPipelineYaml(
                "pipeline:\n  notificationRules:\n    notificationBody:\n      notificationContent: \"test\"")
            .build();
    when(pipelineTemplateHelper.resolveTemplateRefsInPipeline(anyString(), anyString(), anyString(), anyString(),
             eq(true), eq(false), anyString(), anyString(), eq(true)))
        .thenReturn(templateMergeResponseDTO);

    // When: Processing notification template resolution
    notificationHelper.sendNotificationInternal(NotificationContext.builder()
                                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                    .orgIdentifier(ORG_IDENTIFIER)
                                                    .projectIdentifier(PROJECT_IDENTIFIER)
                                                    .yaml(v1NotificationYaml)
                                                    .notificationRulesList(List.of(rules))
                                                    .notificationContent(Map.of("testKey", "testVal"))
                                                    .pipelineEventType(PIPELINE_START)
                                                    .build(),
        v1Ambiance, null);

    // Then: Should call conversion method to convert v1 to v0
    verify(notificationRulesMapper, times(1)).convertTemplateNodeV1ToV0(any());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testProcessNotificationTemplateResolution_V0FlowWithoutTemplate_ShouldUseNotificationBody()
      throws Exception {
    String v0NotificationYaml = "pipeline:\n"
        + "  name: TestPipeline\n"
        + "  identifier: TestPipeline\n"
        + "  notificationRules:\n"
        + "    - name: rule1\n"
        + "      notificationBody:\n"
        + "        variables:\n"
        + "          - name: message\n"
        + "            value: Test message\n"
        + "      pipelineEvents:\n"
        + "        - type: PipelineSuccess\n"
        + "      notificationMethod:\n"
        + "        type: Email\n"
        + "        spec:\n"
        + "          recipients:\n"
        + "            - test@harness.io\n"
        + "      enabled: true\n";

    Ambiance v0Ambiance = Ambiance.newBuilder()
                              .putSetupAbstractions("accountId", ACCOUNT_IDENTIFIER)
                              .putSetupAbstractions("orgIdentifier", ORG_IDENTIFIER)
                              .putSetupAbstractions("projectIdentifier", PROJECT_IDENTIFIER)
                              .setMetadata(ExecutionMetadata.newBuilder()
                                               .setHarnessVersion(HarnessYamlVersion.V0)
                                               .setPipelineIdentifier("TestPipeline")
                                               .build())
                              .setPlanExecutionId("planExecId")
                              .build();

    when(planExecutionMetadataService.findByPlanExecutionId(anyString(), anyString()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().yaml(v0NotificationYaml).build()));

    // When: Processing notification without template
    notificationHelper.sendNotificationInternal(NotificationContext.builder()
                                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                    .orgIdentifier(ORG_IDENTIFIER)
                                                    .projectIdentifier(PROJECT_IDENTIFIER)
                                                    .yaml(v0NotificationYaml)
                                                    .build(),
        v0Ambiance, null);

    // Then: Should NOT call template resolution, should use notificationBody
    verify(notificationRulesMapper, never()).convertTemplateNodeV1ToV0(any());
  }

  // --- Tests for SecurityContext fix in resolveNotificationTemplate (PIPE_DISABLE_NOTIFICATION_SECURITY_CONTEXT_FIX)
  // ---

  /**
   * Helper to invoke the private resolveNotificationTemplate method via reflection.
   */
  private String invokeResolveNotificationTemplate(Ambiance testAmbiance, String accountId, String orgId,
      String projectId, String templateYaml, Map<String, String> inputVarMap, PipelineEventType eventType,
      boolean isResolved, Map<String, String> notificationMapData) throws Exception {
    Method method = NotificationHelper.class.getDeclaredMethod("resolveNotificationTemplate", Ambiance.class,
        String.class, String.class, String.class, String.class, Map.class, PipelineEventType.class,
        io.harness.ngtriggers.beans.dto.TriggerNotificationData.class, boolean.class, Map.class);
    method.setAccessible(true);
    try {
      return (String) method.invoke(notificationHelper, testAmbiance, accountId, orgId, projectId, templateYaml,
          inputVarMap, eventType,
          /*triggerNotificationData=*/null, isResolved, notificationMapData);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw e;
    }
  }

  private Ambiance buildAmbianceWithPrincipal() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "testAccount")
        .putSetupAbstractions("orgIdentifier", "testOrg")
        .putSetupAbstractions("projectIdentifier", "testProject")
        .setMetadata(ExecutionMetadata.newBuilder()
                         .setModuleType("cd")
                         .setPipelineIdentifier("testPipeline")
                         .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder().setShouldValidateRbac(false).build())
                         .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                             .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("dummy").build())
                                             .build())
                         .build())
        .setPlanExecutionId("testPlanExec")
        .build();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testResolveNotificationTemplate_securityContextSetWhenFFOff() throws Exception {
    // Given: FF is OFF (default) → fix is active
    Ambiance testAmbiance = buildAmbianceWithPrincipal();
    when(pmsFeatureFlagHelper.isEnabled("testAccount", FeatureName.PIPE_DISABLE_NOTIFICATION_SECURITY_CONTEXT_FIX))
        .thenReturn(false);

    String templateYaml = "pipeline:\n  notificationRules:\n    - name: test\n";
    TemplateMergeResponseDTO mergeResponse =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml("resolvedYaml").build();
    when(pipelineTemplateHelper.resolveTemplateRefsInPipeline(eq("testAccount"), eq("testOrg"), eq("testProject"),
             eq(templateYaml), eq(true), eq(false), any(), any(), eq(true)))
        .thenReturn(mergeResponse);
    when(pmsEngineExpressionService.resolve(any(), any(), any(), any())).thenReturn("resolvedBody");

    // Clear any existing context
    SecurityContextBuilder.setContext((Principal) null);
    SourcePrincipalContextBuilder.setSourcePrincipal(null);

    // When
    invokeResolveNotificationTemplate(testAmbiance, "testAccount", "testOrg", "testProject", templateYaml,
        new HashMap<>(), PipelineEventType.PIPELINE_SUCCESS, false, new HashMap<>());

    // Then: template resolution should have been called (proving the guard set the context)
    verify(pipelineTemplateHelper, times(1))
        .resolveTemplateRefsInPipeline(eq("testAccount"), eq("testOrg"), eq("testProject"), eq(templateYaml), eq(true),
            eq(false), any(), any(), eq(true));

    // After the guard closes, the original context (null) should be restored
    assertThat(SecurityContextBuilder.getPrincipal()).isNull();
    assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testResolveNotificationTemplate_securityContextRestoredAfterGuard() throws Exception {
    // Given: FF is OFF and there's a pre-existing principal on the thread
    Ambiance testAmbiance = buildAmbianceWithPrincipal();
    when(pmsFeatureFlagHelper.isEnabled("testAccount", FeatureName.PIPE_DISABLE_NOTIFICATION_SECURITY_CONTEXT_FIX))
        .thenReturn(false);

    String templateYaml = "pipeline:\n  notificationRules:\n    - name: test\n";
    TemplateMergeResponseDTO mergeResponse =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml("resolvedYaml").build();
    when(pipelineTemplateHelper.resolveTemplateRefsInPipeline(eq("testAccount"), eq("testOrg"), eq("testProject"),
             eq(templateYaml), eq(true), eq(false), any(), any(), eq(true)))
        .thenReturn(mergeResponse);
    when(pmsEngineExpressionService.resolve(any(), any(), any(), any())).thenReturn("resolvedBody");

    // Set a pre-existing principal (simulating a thread that already had context)
    Principal existingPrincipal = new UserPrincipal("existingUser", "email@test.com", "username", "testAccount");
    SecurityContextBuilder.setContext(existingPrincipal);
    SourcePrincipalContextBuilder.setSourcePrincipal(existingPrincipal);

    // When
    invokeResolveNotificationTemplate(testAmbiance, "testAccount", "testOrg", "testProject", templateYaml,
        new HashMap<>(), PipelineEventType.PIPELINE_SUCCESS, false, new HashMap<>());

    // Then: After the guard closes, the original principal should be restored (no side effects)
    assertThat(SecurityContextBuilder.getPrincipal()).isEqualTo(existingPrincipal);
    assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isEqualTo(existingPrincipal);

    // Cleanup
    SecurityContextBuilder.setContext((Principal) null);
    SourcePrincipalContextBuilder.setSourcePrincipal(null);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testResolveNotificationTemplate_noGuardWhenFFOn() throws Exception {
    // Given: FF is ON → fix is disabled, old behavior
    Ambiance testAmbiance = buildAmbianceWithPrincipal();
    when(pmsFeatureFlagHelper.isEnabled("testAccount", FeatureName.PIPE_DISABLE_NOTIFICATION_SECURITY_CONTEXT_FIX))
        .thenReturn(true);

    String templateYaml = "pipeline:\n  notificationRules:\n    - name: test\n";
    TemplateMergeResponseDTO mergeResponse =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml("resolvedYaml").build();
    when(pipelineTemplateHelper.resolveTemplateRefsInPipeline(eq("testAccount"), eq("testOrg"), eq("testProject"),
             eq(templateYaml), eq(true), eq(false), any(), any(), eq(true)))
        .thenReturn(mergeResponse);
    when(pmsEngineExpressionService.resolve(any(), any(), any(), any())).thenReturn("resolvedBody");

    // Set context to null (simulating the bug scenario)
    SecurityContextBuilder.setContext((Principal) null);

    // When
    invokeResolveNotificationTemplate(testAmbiance, "testAccount", "testOrg", "testProject", templateYaml,
        new HashMap<>(), PipelineEventType.PIPELINE_SUCCESS, false, new HashMap<>());

    // Then: template resolution still called (via the else branch), context remains null
    verify(pipelineTemplateHelper, times(1))
        .resolveTemplateRefsInPipeline(eq("testAccount"), eq("testOrg"), eq("testProject"), eq(templateYaml), eq(true),
            eq(false), any(), any(), eq(true));
    assertThat(SecurityContextBuilder.getPrincipal()).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testResolveNotificationTemplate_runtimeExceptionPropagatesAndContextRestored() throws Exception {
    // Given: FF is OFF, but resolveTemplateRefsInPipeline throws RuntimeException
    Ambiance testAmbiance = buildAmbianceWithPrincipal();
    when(pmsFeatureFlagHelper.isEnabled("testAccount", FeatureName.PIPE_DISABLE_NOTIFICATION_SECURITY_CONTEXT_FIX))
        .thenReturn(false);

    String templateYaml = "pipeline:\n  notificationRules:\n    - name: test\n";
    when(pipelineTemplateHelper.resolveTemplateRefsInPipeline(eq("testAccount"), eq("testOrg"), eq("testProject"),
             eq(templateYaml), eq(true), eq(false), any(), any(), eq(true)))
        .thenThrow(new RuntimeException("Template service unavailable"));

    // Set a pre-existing principal
    Principal existingPrincipal = new ServicePrincipal("some-other-service");
    SecurityContextBuilder.setContext(existingPrincipal);
    SourcePrincipalContextBuilder.setSourcePrincipal(existingPrincipal);

    // When / Then: RuntimeException should propagate
    try {
      invokeResolveNotificationTemplate(testAmbiance, "testAccount", "testOrg", "testProject", templateYaml,
          new HashMap<>(), PipelineEventType.PIPELINE_SUCCESS, false, new HashMap<>());
      // Should not reach here
      assertThat(false).as("Expected RuntimeException to be thrown").isTrue();
    } catch (RuntimeException ex) {
      assertThat(ex.getMessage()).isEqualTo("Template service unavailable");
    }

    // And: The original context should still be restored (guard's close() runs before catch)
    assertThat(SecurityContextBuilder.getPrincipal()).isEqualTo(existingPrincipal);
    assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isEqualTo(existingPrincipal);

    // Cleanup
    SecurityContextBuilder.setContext((Principal) null);
    SourcePrincipalContextBuilder.setSourcePrincipal(null);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testShouldSendNotificationForStepFailed_AllSteps() {
    List<PipelineEvent> events =
        Collections.singletonList(PipelineEvent.builder().type(PipelineEventType.STEP_FAILED).build());
    // forSteps is null => should match all steps (backward compat)
    assertThat(
        notificationHelper.shouldSendNotification(events, PipelineEventType.STEP_FAILED, "stage1", "stage1.my_step"))
        .isTrue();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testShouldSendNotificationForStepFailed_ExplicitAllSteps() {
    List<PipelineEvent> events = Collections.singletonList(
        PipelineEvent.builder()
            .type(PipelineEventType.STEP_FAILED)
            .forSteps(Collections.singletonList(io.harness.pms.yaml.YAMLFieldNameConstants.ALL_STEPS))
            .build());
    assertThat(
        notificationHelper.shouldSendNotification(events, PipelineEventType.STEP_FAILED, "stage1", "stage1.my_step"))
        .isTrue();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testShouldSendNotificationForStepFailed_SpecificStepMatch() {
    List<PipelineEvent> events = Collections.singletonList(PipelineEvent.builder()
                                                               .type(PipelineEventType.STEP_FAILED)
                                                               .forSteps(List.of("stage1.my_step", "stage2.other_step"))
                                                               .build());
    assertThat(
        notificationHelper.shouldSendNotification(events, PipelineEventType.STEP_FAILED, "stage1", "stage1.my_step"))
        .isTrue();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testShouldSendNotificationForStepFailed_SpecificStepNoMatch() {
    List<PipelineEvent> events = Collections.singletonList(
        PipelineEvent.builder().type(PipelineEventType.STEP_FAILED).forSteps(List.of("stage1.other_step")).build());
    assertThat(
        notificationHelper.shouldSendNotification(events, PipelineEventType.STEP_FAILED, "stage1", "stage1.my_step"))
        .isFalse();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testShouldSendNotificationForStepFailed_EmptyForSteps() {
    List<PipelineEvent> events = Collections.singletonList(
        PipelineEvent.builder().type(PipelineEventType.STEP_FAILED).forSteps(Collections.emptyList()).build());
    // empty forSteps => all steps
    assertThat(
        notificationHelper.shouldSendNotification(events, PipelineEventType.STEP_FAILED, "stage1", "stage1.my_step"))
        .isTrue();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testShouldSendNotificationForStepFailed_StepInStepGroup() {
    List<PipelineEvent> events = Collections.singletonList(
        PipelineEvent.builder().type(PipelineEventType.STEP_FAILED).forSteps(List.of("deploy.sg1.inner_step")).build());
    assertThat(notificationHelper.shouldSendNotification(
                   events, PipelineEventType.STEP_FAILED, "deploy", "deploy.sg1.inner_step"))
        .isTrue();
    // Different step in same group should not match
    assertThat(notificationHelper.shouldSendNotification(
                   events, PipelineEventType.STEP_FAILED, "deploy", "deploy.sg1.other_step"))
        .isFalse();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testShouldSendNotification_AllEventsMatchesStepFailed() {
    List<PipelineEvent> events =
        Collections.singletonList(PipelineEvent.builder().type(PipelineEventType.ALL_EVENTS).build());
    assertThat(
        notificationHelper.shouldSendNotification(events, PipelineEventType.STEP_FAILED, "stage1", "stage1.my_step"))
        .isTrue();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testShouldSendNotification_StageLevelUnchanged() {
    List<PipelineEvent> events =
        Collections.singletonList(PipelineEvent.builder().type(STAGE_FAILED).forStages(List.of("stage1")).build());
    // Stage-level events should still work as before
    assertThat(notificationHelper.shouldSendNotification(events, STAGE_FAILED, "stage1", null)).isTrue();
    assertThat(notificationHelper.shouldSendNotification(events, STAGE_FAILED, "stage2", null)).isFalse();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testShouldSendNotification_PipelineLevelUnchanged() {
    List<PipelineEvent> events = Collections.singletonList(PipelineEvent.builder().type(PIPELINE_SUCCESS).build());
    assertThat(notificationHelper.shouldSendNotification(events, PIPELINE_SUCCESS, "", null)).isTrue();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testComputeStepBaseFqnFromAmbiance_StepInsideStepGroup() {
    Level stageLevel = Level.newBuilder()
                           .setIdentifier("deploy")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setGroup("STAGE")
                           .build();
    Level stepGroupLevel =
        Level.newBuilder()
            .setIdentifier("sg1")
            .setStepType(StepType.newBuilder().setType("STEP_GROUP").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP_GROUP")
            .build();
    Level stepLevel =
        Level.newBuilder()
            .setIdentifier("inner_step")
            .setStepType(StepType.newBuilder().setType("ShellScript").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP")
            .build();

    Ambiance ambiance =
        Ambiance.newBuilder().addLevels(stageLevel).addLevels(stepGroupLevel).addLevels(stepLevel).build();

    String fqn = notificationHelper.computeStepBaseFqnFromAmbiance(ambiance);
    assertThat(fqn).isEqualTo("deploy.sg1.inner_step");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testComputeStepBaseFqnFromAmbiance_V1StepGroupUsesGroupStepType() {
    Level stageLevel = Level.newBuilder()
                           .setIdentifier("deploy_v1")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setGroup("STAGE")
                           .build();
    Level stepGroupLevel =
        Level.newBuilder()
            .setIdentifier("sg_v1")
            .setStepType(StepType.newBuilder().setType("GROUP").setStepCategory(StepCategory.STEP).build())
            .setGroup("GROUP")
            .build();
    Level stepLevel = Level.newBuilder()
                          .setIdentifier("run_step")
                          .setStepType(StepType.newBuilder().setType("Run").setStepCategory(StepCategory.STEP).build())
                          .setGroup("STEP")
                          .build();

    Ambiance ambiance =
        Ambiance.newBuilder().addLevels(stageLevel).addLevels(stepGroupLevel).addLevels(stepLevel).build();

    String fqn = notificationHelper.computeStepBaseFqnFromAmbiance(ambiance);
    assertThat(fqn).isEqualTo("deploy_v1.sg_v1.run_step");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testComputeStepBaseFqnFromAmbiance_V1StageGroupThenInnerStageThenStep() {
    // V1 stage group uses GROUP with StepCategory.STAGE (GroupStepV1.GROUP_STAGE_TYPE); FQN must include group id.
    Level stageGroupLevel =
        Level.newBuilder()
            .setIdentifier("my_sg")
            .setStepType(StepType.newBuilder().setType("GROUP").setStepCategory(StepCategory.STAGE).build())
            .setGroup("GROUP")
            .build();
    Level innerStageLevel =
        Level.newBuilder()
            .setIdentifier("inner_deploy")
            .setStepType(StepType.newBuilder().setType("Deployment").setStepCategory(StepCategory.STAGE).build())
            .setGroup("STAGE")
            .build();
    Level stepLevel =
        Level.newBuilder()
            .setIdentifier("shell1")
            .setStepType(StepType.newBuilder().setType("ShellScript").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP")
            .build();

    Ambiance ambiance =
        Ambiance.newBuilder().addLevels(stageGroupLevel).addLevels(innerStageLevel).addLevels(stepLevel).build();

    String fqn = notificationHelper.computeStepBaseFqnFromAmbiance(ambiance);
    assertThat(fqn).isEqualTo("my_sg.inner_deploy.shell1");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testComputeStepBaseFqnFromAmbiance_StepWithoutStepGroup() {
    Level stageLevel = Level.newBuilder()
                           .setIdentifier("deploy")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setGroup("STAGE")
                           .build();
    Level stepLevel =
        Level.newBuilder()
            .setIdentifier("will_fail_step")
            .setStepType(StepType.newBuilder().setType("ShellScript").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP")
            .build();

    Ambiance ambiance = Ambiance.newBuilder().addLevels(stageLevel).addLevels(stepLevel).build();

    String fqn = notificationHelper.computeStepBaseFqnFromAmbiance(ambiance);
    assertThat(fqn).isEqualTo("deploy.will_fail_step");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testComputeStepBaseFqnFromAmbiance_NestedStepGroups() {
    Level stageLevel = Level.newBuilder()
                           .setIdentifier("deploy")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setGroup("STAGE")
                           .build();
    Level outerSg =
        Level.newBuilder()
            .setIdentifier("outer_sg")
            .setStepType(StepType.newBuilder().setType("STEP_GROUP").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP_GROUP")
            .build();
    Level innerSg =
        Level.newBuilder()
            .setIdentifier("inner_sg")
            .setStepType(StepType.newBuilder().setType("STEP_GROUP").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP_GROUP")
            .build();
    Level stepLevel =
        Level.newBuilder()
            .setIdentifier("my_step")
            .setStepType(StepType.newBuilder().setType("ShellScript").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP")
            .build();

    Ambiance ambiance =
        Ambiance.newBuilder().addLevels(stageLevel).addLevels(outerSg).addLevels(innerSg).addLevels(stepLevel).build();

    String fqn = notificationHelper.computeStepBaseFqnFromAmbiance(ambiance);
    assertThat(fqn).isEqualTo("deploy.outer_sg.inner_sg.my_step");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testComputeStepBaseFqnFromAmbiance_StepInsideInsert() {
    Level stageLevel = Level.newBuilder()
                           .setIdentifier("deploy")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setGroup("STAGE")
                           .build();
    Level insertLevel =
        Level.newBuilder()
            .setIdentifier("my_insert")
            .setStepType(StepType.newBuilder().setType("INSERT").setStepCategory(StepCategory.INSERT).build())
            .setGroup("INSERT")
            .build();
    Level stepLevel =
        Level.newBuilder()
            .setIdentifier("injected_step")
            .setStepType(StepType.newBuilder().setType("ShellScript").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP")
            .build();

    Ambiance ambiance = Ambiance.newBuilder().addLevels(stageLevel).addLevels(insertLevel).addLevels(stepLevel).build();

    String fqn = notificationHelper.computeStepBaseFqnFromAmbiance(ambiance);
    assertThat(fqn).isEqualTo("deploy.my_insert.injected_step");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testComputeStepBaseFqnFromAmbiance_ParallelAndStrategyAreTransparent() {
    Level stageLevel = Level.newBuilder()
                           .setIdentifier("deploy")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setGroup("STAGE")
                           .build();
    Level forkLevel = Level.newBuilder()
                          .setIdentifier("parallel_id")
                          .setStepType(StepType.newBuilder().setStepCategory(StepCategory.FORK).build())
                          .build();
    Level strategyLevel = Level.newBuilder()
                              .setIdentifier("strategy_id")
                              .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                              .build();
    Level stepLevel =
        Level.newBuilder()
            .setIdentifier("my_step")
            .setStepType(StepType.newBuilder().setType("ShellScript").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP")
            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(stageLevel)
                            .addLevels(forkLevel)
                            .addLevels(strategyLevel)
                            .addLevels(stepLevel)
                            .build();

    String fqn = notificationHelper.computeStepBaseFqnFromAmbiance(ambiance);
    assertThat(fqn).isEqualTo("deploy.my_step");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testComputeStepBaseFqnFromAmbiance_RepeatStrategyWithStrategyInfo() {
    Level stageLevel = Level.newBuilder()
                           .setIdentifier("deploy")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setGroup("STAGE")
                           .build();
    Level strategyLevel = Level.newBuilder()
                              .setIdentifier("loop_step")
                              .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                              .build();
    Level stepLevel =
        Level.newBuilder()
            .setIdentifier("loop_step_0")
            .setStepType(StepType.newBuilder().setType("ShellScript").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP")
            .setStrategyInfo(StrategyInfo.newBuilder().setIdentifierPostFix("_0").build())
            .build();

    Ambiance ambiance =
        Ambiance.newBuilder().addLevels(stageLevel).addLevels(strategyLevel).addLevels(stepLevel).build();

    String fqn = notificationHelper.computeStepBaseFqnFromAmbiance(ambiance);
    assertThat(fqn).isEqualTo("deploy.loop_step");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testComputeStepBaseFqnFromAmbiance_MatrixStrategyWithStrategyInfo() {
    Level stageLevel = Level.newBuilder()
                           .setIdentifier("deploy")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setGroup("STAGE")
                           .build();
    Level strategyLevel = Level.newBuilder()
                              .setIdentifier("matrix_step")
                              .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                              .build();
    Level stepLevel =
        Level.newBuilder()
            .setIdentifier("matrix_step_dev")
            .setStepType(StepType.newBuilder().setType("ShellScript").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP")
            .setStrategyInfo(StrategyInfo.newBuilder().setIdentifierPostFix("_dev").build())
            .build();

    Ambiance ambiance =
        Ambiance.newBuilder().addLevels(stageLevel).addLevels(strategyLevel).addLevels(stepLevel).build();

    String fqn = notificationHelper.computeStepBaseFqnFromAmbiance(ambiance);
    assertThat(fqn).isEqualTo("deploy.matrix_step");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testComputeStepBaseFqnFromAmbiance_RepeatStrategyWithDeprecatedStrategyMetadata() {
    Level stageLevel = Level.newBuilder()
                           .setIdentifier("deploy")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setGroup("STAGE")
                           .build();
    Level stepLevel =
        Level.newBuilder()
            .setIdentifier("loop_step_1")
            .setStepType(StepType.newBuilder().setType("ShellScript").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP")
            .setStrategyMetadata(StrategyMetadata.newBuilder().setIdentifierPostFix("_1").build())
            .build();

    Ambiance ambiance = Ambiance.newBuilder().addLevels(stageLevel).addLevels(stepLevel).build();

    String fqn = notificationHelper.computeStepBaseFqnFromAmbiance(ambiance);
    assertThat(fqn).isEqualTo("deploy.loop_step");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testComputeStepBaseFqnFromAmbiance_StepInStepGroupWithStrategy() {
    Level stageLevel = Level.newBuilder()
                           .setIdentifier("deploy")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setGroup("STAGE")
                           .build();
    Level stepGroupLevel =
        Level.newBuilder()
            .setIdentifier("sg1")
            .setStepType(StepType.newBuilder().setType("STEP_GROUP").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP_GROUP")
            .build();
    Level strategyLevel = Level.newBuilder()
                              .setIdentifier("http_call")
                              .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                              .build();
    Level stepLevel =
        Level.newBuilder()
            .setIdentifier("http_call_0")
            .setStepType(StepType.newBuilder().setType("ShellScript").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP")
            .setStrategyInfo(StrategyInfo.newBuilder().setIdentifierPostFix("_0").build())
            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(stageLevel)
                            .addLevels(stepGroupLevel)
                            .addLevels(strategyLevel)
                            .addLevels(stepLevel)
                            .build();

    String fqn = notificationHelper.computeStepBaseFqnFromAmbiance(ambiance);
    assertThat(fqn).isEqualTo("deploy.sg1.http_call");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testComputeStepBaseFqnFromAmbiance_StrategyOnStepGroupItself() {
    Level stageLevel = Level.newBuilder()
                           .setIdentifier("deploy")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setGroup("STAGE")
                           .build();
    Level sgStrategyLevel = Level.newBuilder()
                                .setIdentifier("sg1")
                                .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                                .build();
    Level stepGroupLevel =
        Level.newBuilder()
            .setIdentifier("sg1_us")
            .setStepType(StepType.newBuilder().setType("STEP_GROUP").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP_GROUP")
            .setStrategyInfo(StrategyInfo.newBuilder().setIdentifierPostFix("_us").build())
            .build();
    Level stepLevel =
        Level.newBuilder()
            .setIdentifier("my_step")
            .setStepType(StepType.newBuilder().setType("ShellScript").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP")
            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(stageLevel)
                            .addLevels(sgStrategyLevel)
                            .addLevels(stepGroupLevel)
                            .addLevels(stepLevel)
                            .build();

    String fqn = notificationHelper.computeStepBaseFqnFromAmbiance(ambiance);
    assertThat(fqn).isEqualTo("deploy.sg1.my_step");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testComputeStepBaseFqnFromAmbiance_StrategyOnBothStepGroupAndStep() {
    Level stageLevel = Level.newBuilder()
                           .setIdentifier("deploy")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setGroup("STAGE")
                           .build();
    Level sgStrategyLevel = Level.newBuilder()
                                .setIdentifier("sg1")
                                .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                                .build();
    Level stepGroupLevel =
        Level.newBuilder()
            .setIdentifier("sg1_eu")
            .setStepType(StepType.newBuilder().setType("STEP_GROUP").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP_GROUP")
            .setStrategyInfo(StrategyInfo.newBuilder().setIdentifierPostFix("_eu").build())
            .build();
    Level stepStrategyLevel = Level.newBuilder()
                                  .setIdentifier("http_call")
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                                  .build();
    Level stepLevel =
        Level.newBuilder()
            .setIdentifier("http_call_0")
            .setStepType(StepType.newBuilder().setType("ShellScript").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP")
            .setStrategyInfo(StrategyInfo.newBuilder().setIdentifierPostFix("_0").build())
            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(stageLevel)
                            .addLevels(sgStrategyLevel)
                            .addLevels(stepGroupLevel)
                            .addLevels(stepStrategyLevel)
                            .addLevels(stepLevel)
                            .build();

    String fqn = notificationHelper.computeStepBaseFqnFromAmbiance(ambiance);
    assertThat(fqn).isEqualTo("deploy.sg1.http_call");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testComputeStepBaseFqnFromAmbiance_StageWithStrategyAndStepInside() {
    Level stageLevel = Level.newBuilder()
                           .setIdentifier("deploy_dev")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setGroup("STAGE")
                           .setStrategyInfo(StrategyInfo.newBuilder().setIdentifierPostFix("_dev").build())
                           .build();
    Level stepLevel =
        Level.newBuilder()
            .setIdentifier("my_step")
            .setStepType(StepType.newBuilder().setType("ShellScript").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP")
            .build();

    Ambiance ambiance = Ambiance.newBuilder().addLevels(stageLevel).addLevels(stepLevel).build();

    String fqn = notificationHelper.computeStepBaseFqnFromAmbiance(ambiance);
    assertThat(fqn).isEqualTo("deploy.my_step");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testComputeStepBaseFqnFromAmbiance_NestedStepGroupsWithStrategyOnInner() {
    Level stageLevel = Level.newBuilder()
                           .setIdentifier("deploy")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setGroup("STAGE")
                           .build();
    Level outerSg =
        Level.newBuilder()
            .setIdentifier("outer_sg")
            .setStepType(StepType.newBuilder().setType("STEP_GROUP").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP_GROUP")
            .build();
    Level innerSgStrategy = Level.newBuilder()
                                .setIdentifier("inner_sg")
                                .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                                .build();
    Level innerSg =
        Level.newBuilder()
            .setIdentifier("inner_sg_dev")
            .setStepType(StepType.newBuilder().setType("STEP_GROUP").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP_GROUP")
            .setStrategyInfo(StrategyInfo.newBuilder().setIdentifierPostFix("_dev").build())
            .build();
    Level stepLevel =
        Level.newBuilder()
            .setIdentifier("my_step")
            .setStepType(StepType.newBuilder().setType("ShellScript").setStepCategory(StepCategory.STEP).build())
            .setGroup("STEP")
            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(stageLevel)
                            .addLevels(outerSg)
                            .addLevels(innerSgStrategy)
                            .addLevels(innerSg)
                            .addLevels(stepLevel)
                            .build();

    String fqn = notificationHelper.computeStepBaseFqnFromAmbiance(ambiance);
    assertThat(fqn).isEqualTo("deploy.outer_sg.inner_sg.my_step");
  }
}
