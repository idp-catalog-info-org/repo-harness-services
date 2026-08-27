/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.PIPE_ALLOW_MULTIPLE_FILEPATH_CONDITIONS;
import static io.harness.delegate.beans.connector.scm.GitAuthType.HTTP;
import static io.harness.ngtriggers.Constants.CHANGED_FILES;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.EQUALS;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.REGEX;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SHIVAM;

import static software.wings.beans.TaskType.SCM_PATH_FILTER_EVALUATION_TASK;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.PRWebhookEvent;
import io.harness.beans.Repository;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.utils.HarnessCodeConnectorUtils;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.HarnessAuthenticationDTO;
import io.harness.delegate.beans.connector.HarnessConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessApiAccessDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.harness.HarnessHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.intfc.ScmConnector;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskParams;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskResponse;
import io.harness.delegate.task.scm.TriggerFilepathResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.ScmPathFilterTaskException;
import io.harness.ng.core.NGAccess;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition;
import io.harness.ngtriggers.conditionchecker.ConditionEvaluator;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.utils.ChangedFilesUtils;
import io.harness.ngtriggers.utils.SCMFilePathEvaluatorFactory;
import io.harness.ngtriggers.utils.SCMFilePathEvaluatorOnDelegate;
import io.harness.ngtriggers.utils.SCMFilePathEvaluatorOnManager;
import io.harness.ngtriggers.utils.TaskExecutionUtils;
import io.harness.polling.contracts.BuildInfo;
import io.harness.polling.contracts.Metadata;
import io.harness.polling.contracts.PollingResponse;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.Issue;
import io.harness.product.ci.scm.proto.IssueCommentHook;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.rule.Owner;
import io.harness.secrets.SecretDecryptor;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;
import io.harness.utils.ConnectorUtils;
import io.harness.utils.PmsFeatureFlagEvaluator;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class TriggerFilePathConditionFilterTest extends CategoryTest {
  @Mock private TaskExecutionUtils taskExecutionUtils;
  @InjectMocks @Inject private NGTriggerElementMapper ngTriggerElementMapper;
  @Mock private NGTriggerService ngTriggerService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private KryoSerializer referenceFalseKryoSerializer;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private SecretDecryptor secretDecryptor;
  @Mock private TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  @Inject @InjectMocks private FilepathTriggerFilter filter;
  @Mock private SCMFilePathEvaluatorOnDelegate scmFilePathEvaluatorOnDelegate;
  @Mock private PmsFeatureFlagEvaluator pmsFeatureFlagEvaluator;
  @Mock private SCMFilePathEvaluatorOnManager scmFilePathEvaluatorOnManager;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @InjectMocks private SCMFilePathEvaluatorFactory scmFilePathEvaluatorFactory;
  @Mock private HarnessCodeConnectorUtils harnessCodeConnectorUtils;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;
  private static List<NGTriggerEntity> triggerEntities;
  private MockedStatic<ConditionEvaluator> aStatic;

  private static Repository repository1 = Repository.builder()
                                              .httpURL("https://github.com/owner1/repo1.git")
                                              .sshURL("git@github.com:owner1/repo1.git")
                                              .link("https://github.com/owner1/repo1/b")
                                              .build();

  String pushPayload = "{\"commits\": [\n"
      + "  {\n"
      + "    \"id\": \"3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"tree_id\": \"cc8524297287b55f07e38c56ecd43625f935252f\",\n"
      + "    \"distinct\": true,\n"
      + "    \"message\": \"nn\",\n"
      + "    \"timestamp\": \"2021-06-25T14:52:49-07:00\",\n"
      + "    \"url\": \"https://github.com/wings-software/cicddemo/commit/3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"author\": {\n"
      + "      \"name\": \"Adwait Bhandare\",\n"
      + "      \"email\": \"adwait.bhandare@harness.io\",\n"
      + "      \"username\": \"adwaitabhandare\"\n"
      + "    },\n"
      + "    \"committer\": {\n"
      + "      \"name\": \"GitHub\",\n"
      + "      \"email\": \"noreply@github.com\",\n"
      + "      \"username\": \"web-flow\"\n"
      + "    },\n"
      + "    \"added\": [\n"
      + "      \"spec/manifest1.yml\"\n"
      + "    ],\n"
      + "    \"removed\": [\n"
      + "      \"File1_Removed.txt\"\n"
      + "    ],\n"
      + "    \"modified\": [\n"
      + "      \"values/value1.yml\"\n"
      + "    ]\n"
      + "  }, \n"
      + "  {\n"
      + "    \"id\": \"3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"tree_id\": \"cc8524297287b55f07e38c56ecd43625f935252f\",\n"
      + "    \"distinct\": true,\n"
      + "    \"message\": \"nn\",\n"
      + "    \"timestamp\": \"2021-06-25T14:52:49-07:00\",\n"
      + "    \"url\": \"https://github.com/wings-software/cicddemo/commit/3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"author\": {\n"
      + "      \"name\": \"Adwait Bhandare\",\n"
      + "      \"email\": \"adwait.bhandare@harness.io\",\n"
      + "      \"username\": \"adwaitabhandare\"\n"
      + "    },\n"
      + "    \"committer\": {\n"
      + "      \"name\": \"GitHub\",\n"
      + "      \"email\": \"noreply@github.com\",\n"
      + "      \"username\": \"web-flow\"\n"
      + "    },\n"
      + "    \"added\": [\n"
      + "      \"spec/manifest2.yml\"\n"
      + "    ],\n"
      + "    \"removed\": [\n"
      + "      \"File2_Removed.txt\"\n"
      + "    ],\n"
      + "    \"modified\": [\n"
      + "      \"values/value2.yml\"\n"
      + "    ]\n"
      + "  }\n"
      + "]}";

  private Set<String> pushPayloadChangedFiles() {
    return new HashSet<>(asList("spec/manifest1.yml", "spec/manifest2.yml", "File1_Removed.txt", "File2_Removed.txt",
        "values/value1.yml", "values/value2.yml"));
  }

  String pushPayloadWithNoCommits = "{\"commits\": [], \n"
      + " \"head_commit\": {\n"
      + "    \"id\": \"3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"tree_id\": \"cc8524297287b55f07e38c56ecd43625f935252f\",\n"
      + "    \"distinct\": true,\n"
      + "    \"message\": \"nn\",\n"
      + "    \"timestamp\": \"2021-06-25T14:52:49-07:00\",\n"
      + "    \"url\": \"https://github.com/wings-software/cicddemo/commit/3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"author\": {\n"
      + "      \"name\": \"Adwait Bhandare\",\n"
      + "      \"email\": \"adwait.bhandare@harness.io\",\n"
      + "      \"username\": \"adwaitabhandare\"\n"
      + "    },\n"
      + "    \"committer\": {\n"
      + "      \"name\": \"GitHub\",\n"
      + "      \"email\": \"noreply@github.com\",\n"
      + "      \"username\": \"web-flow\"\n"
      + "    },\n"
      + "    \"added\": [\n"
      + "      \"spec/manifest2.yml\"\n"
      + "    ],\n"
      + "    \"removed\": [\n"
      + "      \"File2_Removed.txt\"\n"
      + "    ],\n"
      + "    \"modified\": [\n"
      + "      \"values/value2.yml\"\n"
      + "    ]\n"
      + "  }}\n";

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    aStatic = mockStatic(ConditionEvaluator.class, CALLS_REAL_METHODS);
    on(filter).set("scmFilePathEvaluatorFactory", scmFilePathEvaluatorFactory);
    on(filter).set("scopeResolutionHelper", scopeResolutionHelper);
    on(scmFilePathEvaluatorOnManager).set("secretDecryptor", secretDecryptor);
    on(scmFilePathEvaluatorOnManager).set("pmsFeatureFlagEvaluator", pmsFeatureFlagEvaluator);
    on(scmFilePathEvaluatorOnDelegate).set("taskExecutionUtils", taskExecutionUtils);
    on(scmFilePathEvaluatorOnDelegate).set("kryoSerializer", kryoSerializer);
    on(scmFilePathEvaluatorOnDelegate).set("referenceFalseKryoSerializer", referenceFalseKryoSerializer);
    on(scmFilePathEvaluatorOnDelegate).set("taskSetupAbstractionHelper", taskSetupAbstractionHelper);
    on(scmFilePathEvaluatorOnDelegate).set("pmsFeatureFlagEvaluator", pmsFeatureFlagEvaluator);
    doAnswer(invocation -> {
      List<String> parentUniqueIds = invocation.getArgument(1);
      Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
      for (String parentUniqueId : parentUniqueIds) {
        scopeInfoMap.put(parentUniqueId,
            Optional.of(ScopeInfo.builder()
                            .accountIdentifier("acc")
                            .orgIdentifier("org")
                            .projectIdentifier("proj")
                            .uniqueId(parentUniqueId)
                            .scopeType(ScopeLevel.PROJECT)
                            .build()));
      }
      return scopeInfoMap;
    })
        .when(scopeResolutionHelper)
        .getScopeInfos(anyString(), anyList());
  }

  @After
  public void cleanup() {
    aStatic.close();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testPathFilterEvaluationNotNeeded() {
    FilterRequestData filterRequestData =
        FilterRequestData.builder().webhookPayloadData(WebhookPayloadData.builder().build()).build();
    assertThat(filter.pathFilterEvaluationNotNeeded(filterRequestData)).isTrue();

    filterRequestData.setWebhookPayloadData(
        WebhookPayloadData.builder()
            .originalEvent(TriggerWebhookEvent.builder()
                               .sourceRepoType(WebhookTriggerType.AWS_CODECOMMIT.getEntityMetadataName())
                               .build())
            .build());
    assertThat(filter.pathFilterEvaluationNotNeeded(filterRequestData)).isTrue();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testInitiateDelegateTaskAndEvaluateForPR() {
    // Init Data
    TriggerDetails triggerDetails = generateTriggerDetails();

    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .apiAccess(GithubApiAccessDTO.builder().spec(GithubTokenSpecDTO.builder().build()).build())
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("usermane")
                                                                          .passwordRef(SecretRefData.builder().build())
                                                                          .build())
                                                 .build())
                                .build())
            .build();

    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    // Mock apis
    doReturn(ConnectorDetails.builder()
                 .connectorConfig(githubConnectorDTO)
                 .connectorType(ConnectorType.GITHUB)
                 .encryptedDataDetails(encryptedDataDetails)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    when(scmFilePathEvaluatorOnDelegate.getScmPathFilterEvaluationTaskParams(
             any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnDelegate.execute(any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();

    byte[] data = new byte[0];
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().data(data).build());
    doReturn(ScmPathFilterEvaluationTaskResponse.builder().matched(true).build())
        .when(kryoSerializer)
        .asInflatedObject(data);
    doReturn(null).when(taskSetupAbstractionHelper).getOwner(any(), any(), any());

    ArgumentCaptor<DelegateTaskRequest> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition, null, false))
        .isTrue();
    verify(taskExecutionUtils, times(1)).executeSyncTask(argumentCaptor.capture());

    // Assert Delegate Task request object generated
    DelegateTaskRequest delegateTaskRequest = argumentCaptor.getValue();
    assertThat(delegateTaskRequest.getAccountId()).isEqualTo("acc");
    assertThat(delegateTaskRequest.getTaskType()).isEqualTo(SCM_PATH_FILTER_EVALUATION_TASK.toString());

    assertThat(delegateTaskRequest.getTaskParameters()).isNotNull();

    TaskParameters taskParameters = delegateTaskRequest.getTaskParameters();
    assertThat(ScmPathFilterEvaluationTaskParams.class.isAssignableFrom(taskParameters.getClass()));
    ScmPathFilterEvaluationTaskParams params = (ScmPathFilterEvaluationTaskParams) taskParameters;
    assertThat(params.getScmConnector()).isEqualTo(githubConnectorDTO);
    assertThat(params.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
    assertThat(params.getPrNumber()).isEqualTo(2);
    assertThat(params.getOperator()).isEqualTo(EQUALS.getValue());
    assertThat(params.getStandard()).isEqualTo("test");

    // DelegateTask returns Error
    doReturn(ErrorNotifyResponseData.builder().errorMessage("error").build())
        .when(kryoSerializer)
        .asInflatedObject(data);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition, null, false))
        .isFalse();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testInitiateDelegateTaskAndEvaluateForPRUsingKryoWithoutReference() {
    // Init Data
    TriggerDetails triggerDetails = generateTriggerDetails();

    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .apiAccess(GithubApiAccessDTO.builder().spec(GithubTokenSpecDTO.builder().build()).build())
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("usermane")
                                                                          .passwordRef(SecretRefData.builder().build())
                                                                          .build())
                                                 .build())
                                .build())
            .build();

    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    // Mock apis
    doReturn(ConnectorDetails.builder()
                 .connectorConfig(githubConnectorDTO)
                 .connectorType(ConnectorType.GITHUB)
                 .encryptedDataDetails(encryptedDataDetails)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    when(scmFilePathEvaluatorOnDelegate.getScmPathFilterEvaluationTaskParams(
             any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnDelegate.execute(any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();

    byte[] data = new byte[0];
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().data(data).usingKryoWithoutReference(true).build());
    doReturn(ScmPathFilterEvaluationTaskResponse.builder().matched(true).build())
        .when(referenceFalseKryoSerializer)
        .asInflatedObject(data);
    doReturn(null).when(taskSetupAbstractionHelper).getOwner(any(), any(), any());

    ArgumentCaptor<DelegateTaskRequest> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition, null, false))
        .isTrue();
    verify(taskExecutionUtils, times(1)).executeSyncTask(argumentCaptor.capture());

    // Assert Delegate Task request object generated
    DelegateTaskRequest delegateTaskRequest = argumentCaptor.getValue();
    assertThat(delegateTaskRequest.getAccountId()).isEqualTo("acc");
    assertThat(delegateTaskRequest.getTaskType()).isEqualTo(SCM_PATH_FILTER_EVALUATION_TASK.toString());

    assertThat(delegateTaskRequest.getTaskParameters()).isNotNull();

    TaskParameters taskParameters = delegateTaskRequest.getTaskParameters();
    assertThat(ScmPathFilterEvaluationTaskParams.class.isAssignableFrom(taskParameters.getClass()));
    ScmPathFilterEvaluationTaskParams params = (ScmPathFilterEvaluationTaskParams) taskParameters;
    assertThat(params.getScmConnector()).isEqualTo(githubConnectorDTO);
    assertThat(params.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
    assertThat(params.getPrNumber()).isEqualTo(2);
    assertThat(params.getOperator()).isEqualTo(EQUALS.getValue());
    assertThat(params.getStandard()).isEqualTo("test");

    // DelegateTask returns Error
    doReturn(ErrorNotifyResponseData.builder().errorMessage("error").build())
        .when(referenceFalseKryoSerializer)
        .asInflatedObject(data);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition, null, false))
        .isFalse();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testInitiateManagerTaskAndEvaluateForPR() {
    TriggerDetails triggerDetails = generateTriggerDetails();
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .apiAccess(GithubApiAccessDTO.builder().spec(GithubTokenSpecDTO.builder().build()).build())
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("usermane")
                                                                          .passwordRef(SecretRefData.builder().build())
                                                                          .build())
                                                 .build())
                                .build())
            .build();

    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    doReturn(ConnectorDetails.builder()
                 .connectorConfig(githubConnectorDTO)
                 .connectorType(ConnectorType.GITHUB)
                 .encryptedDataDetails(encryptedDataDetails)
                 .executeOnDelegate(false)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    when(scmFilePathEvaluatorOnManager.execute(any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnManager.getScmPathFilterEvaluationTaskParams(
             any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnManager.getChangedFileset(any(), any(), any()))
        .thenReturn(new HashSet<>(Collections.singletonList("file")));

    when(ConditionEvaluator.evaluate(any(), any(), any())).thenReturn(true);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition, null, false))
        .isTrue();
  }

  private TriggerDetails generateTriggerDetails() {
    NGTriggerEntity ngTriggerEntity =
        NGTriggerEntity.builder()
            .identifier("id")
            .accountId("acc")
            .orgIdentifier("org")
            .projectIdentifier("proj")
            .parentUniqueId("uniqueId")
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder()
                                       .git(GitMetadata.builder().connectorIdentifier("account.conn").build())
                                       .build())
                          .build())
            .build();
    return TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testInitiateDelegateTaskAndEvaluateForPush() {
    // Init Data
    final String url = "url";
    final String validationRepo = "validationRepo";
    TriggerDetails triggerDetails = generateTriggerDetails();

    final GitlabAuthenticationDTO gitlabAuthenticationDTO =
        GitlabAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(GitlabHttpCredentialsDTO.builder()
                             .type(GitlabHttpAuthenticationType.USERNAME_AND_PASSWORD)
                             .httpCredentialsSpec(GitlabUsernamePasswordDTO.builder()
                                                      .passwordRef(SecretRefHelper.createSecretRef("passwordRef"))
                                                      .username("username")
                                                      .build())
                             .build())
            .build();

    final GitlabApiAccessDTO gitlabApiAccessDTO =
        GitlabApiAccessDTO.builder()
            .type(GitlabApiAccessType.TOKEN)
            .spec(GitlabTokenSpecDTO.builder().tokenRef(SecretRefHelper.createSecretRef("privateKeyRef")).build())
            .build();

    GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder()
                                                .url(url)
                                                .validationRepo(validationRepo)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .authentication(gitlabAuthenticationDTO)
                                                .apiAccess(gitlabApiAccessDTO)
                                                .build();
    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPush(PushHook.newBuilder().setBefore("before").setAfter("after").setRef("ref").build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    // Mock apis
    doReturn(ConnectorDetails.builder()
                 .connectorConfig(gitlabConnectorDTO)
                 .orgIdentifier("org")
                 .connectorType(ConnectorType.GITLAB)
                 .projectIdentifier("proj")
                 .encryptedDataDetails(encryptedDataDetails)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    when(scmFilePathEvaluatorOnDelegate.getScmPathFilterEvaluationTaskParams(
             any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnDelegate.execute(any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();

    byte[] data = new byte[0];
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().data(data).build());
    doReturn(ScmPathFilterEvaluationTaskResponse.builder().matched(true).build())
        .when(kryoSerializer)
        .asInflatedObject(data);
    doReturn(null).when(taskSetupAbstractionHelper).getOwner(any(), any(), any());

    ArgumentCaptor<DelegateTaskRequest> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition, null, false))
        .isTrue();
    verify(taskExecutionUtils, times(1)).executeSyncTask(argumentCaptor.capture());

    // Assert Delegate Task request object generated
    DelegateTaskRequest delegateTaskRequest = argumentCaptor.getValue();
    assertThat(delegateTaskRequest.getAccountId()).isEqualTo("acc");
    assertThat(delegateTaskRequest.getTaskType()).isEqualTo(SCM_PATH_FILTER_EVALUATION_TASK.toString());

    assertThat(delegateTaskRequest.getTaskParameters()).isNotNull();

    TaskParameters taskParameters = delegateTaskRequest.getTaskParameters();
    assertThat(ScmPathFilterEvaluationTaskParams.class.isAssignableFrom(taskParameters.getClass()));
    ScmPathFilterEvaluationTaskParams params = (ScmPathFilterEvaluationTaskParams) taskParameters;
    assertThat(params.getScmConnector()).isEqualTo(gitlabConnectorDTO);
    assertThat(params.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
    assertThat(params.getPreviousCommit()).isEqualTo("before");
    assertThat(params.getLatestCommit()).isEqualTo("after");
    assertThat(params.getBranch()).isEqualTo("ref");
    assertThat(params.getOperator()).isEqualTo(EQUALS.getValue());
    assertThat(params.getStandard()).isEqualTo("test");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testInitiateDelegateTaskAndEvaluateForPushUsingKryoWithoutReference() {
    // Init Data
    final String url = "url";
    final String validationRepo = "validationRepo";
    TriggerDetails triggerDetails = generateTriggerDetails();

    final GitlabAuthenticationDTO gitlabAuthenticationDTO =
        GitlabAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(GitlabHttpCredentialsDTO.builder()
                             .type(GitlabHttpAuthenticationType.USERNAME_AND_PASSWORD)
                             .httpCredentialsSpec(GitlabUsernamePasswordDTO.builder()
                                                      .passwordRef(SecretRefHelper.createSecretRef("passwordRef"))
                                                      .username("username")
                                                      .build())
                             .build())
            .build();

    final GitlabApiAccessDTO gitlabApiAccessDTO =
        GitlabApiAccessDTO.builder()
            .type(GitlabApiAccessType.TOKEN)
            .spec(GitlabTokenSpecDTO.builder().tokenRef(SecretRefHelper.createSecretRef("privateKeyRef")).build())
            .build();

    GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder()
                                                .url(url)
                                                .validationRepo(validationRepo)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .authentication(gitlabAuthenticationDTO)
                                                .apiAccess(gitlabApiAccessDTO)
                                                .build();
    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPush(PushHook.newBuilder().setBefore("before").setAfter("after").setRef("ref").build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    // Mock apis
    doReturn(ConnectorDetails.builder()
                 .connectorConfig(gitlabConnectorDTO)
                 .orgIdentifier("org")
                 .connectorType(ConnectorType.GITLAB)
                 .projectIdentifier("proj")
                 .encryptedDataDetails(encryptedDataDetails)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    when(scmFilePathEvaluatorOnDelegate.getScmPathFilterEvaluationTaskParams(
             any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnDelegate.execute(any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();

    byte[] data = new byte[0];
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().data(data).usingKryoWithoutReference(true).build());
    doReturn(ScmPathFilterEvaluationTaskResponse.builder().matched(true).build())
        .when(referenceFalseKryoSerializer)
        .asInflatedObject(data);
    doReturn(null).when(taskSetupAbstractionHelper).getOwner(any(), any(), any());

    ArgumentCaptor<DelegateTaskRequest> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition, null, false))
        .isTrue();
    verify(taskExecutionUtils, times(1)).executeSyncTask(argumentCaptor.capture());

    // Assert Delegate Task request object generated
    DelegateTaskRequest delegateTaskRequest = argumentCaptor.getValue();
    assertThat(delegateTaskRequest.getAccountId()).isEqualTo("acc");
    assertThat(delegateTaskRequest.getTaskType()).isEqualTo(SCM_PATH_FILTER_EVALUATION_TASK.toString());

    assertThat(delegateTaskRequest.getTaskParameters()).isNotNull();

    TaskParameters taskParameters = delegateTaskRequest.getTaskParameters();
    assertThat(ScmPathFilterEvaluationTaskParams.class.isAssignableFrom(taskParameters.getClass()));
    ScmPathFilterEvaluationTaskParams params = (ScmPathFilterEvaluationTaskParams) taskParameters;
    assertThat(params.getScmConnector()).isEqualTo(gitlabConnectorDTO);
    assertThat(params.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
    assertThat(params.getPreviousCommit()).isEqualTo("before");
    assertThat(params.getLatestCommit()).isEqualTo("after");
    assertThat(params.getBranch()).isEqualTo("ref");
    assertThat(params.getOperator()).isEqualTo(EQUALS.getValue());
    assertThat(params.getStandard()).isEqualTo("test");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testInitiateManagerTaskAndEvaluateForPush() {
    TriggerDetails triggerDetails = generateTriggerDetails();

    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .apiAccess(GithubApiAccessDTO.builder().spec(GithubTokenSpecDTO.builder().build()).build())
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("usermane")
                                                                          .passwordRef(SecretRefData.builder().build())
                                                                          .build())
                                                 .build())
                                .build())
            .build();

    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPush(PushHook.newBuilder().setBefore("before").setAfter("after").setRef("ref").build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    doReturn(ConnectorDetails.builder()
                 .connectorConfig(githubConnectorDTO)
                 .orgIdentifier("org")
                 .connectorType(ConnectorType.GITHUB)
                 .projectIdentifier("proj")
                 .encryptedDataDetails(encryptedDataDetails)
                 .executeOnDelegate(false)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    when(scmFilePathEvaluatorOnManager.execute(any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnManager.getScmPathFilterEvaluationTaskParams(
             any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnManager.getChangedFileset(any(), any(), any()))
        .thenReturn(new HashSet<>(Collections.singletonList("file")));

    when(ConditionEvaluator.evaluate(any(), any(), any())).thenReturn(true);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition, null, false))
        .isTrue();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testInitiateTaskAndEvaluateForGithubAPP() {
    TriggerDetails triggerDetails = generateTriggerDetails();

    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .apiAccess(GithubApiAccessDTO.builder()
                           .type(GithubApiAccessType.GITHUB_APP)
                           .spec(GithubAppSpecDTO.builder().build())
                           .build())
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("usermane")
                                                                          .passwordRef(SecretRefData.builder().build())
                                                                          .build())
                                                 .build())
                                .build())
            .build();

    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPush(PushHook.newBuilder().setBefore("before").setAfter("after").setRef("ref").build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    doReturn(ConnectorDetails.builder()
                 .connectorConfig(githubConnectorDTO)
                 .orgIdentifier("org")
                 .connectorType(ConnectorType.GITHUB)
                 .projectIdentifier("proj")
                 .encryptedDataDetails(encryptedDataDetails)
                 .executeOnDelegate(false)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    when(scmFilePathEvaluatorOnManager.execute(any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnManager.getScmPathFilterEvaluationTaskParams(
             any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnManager.getChangedFileset(any(), any(), any()))
        .thenReturn(new HashSet<>(Collections.singletonList("file")));

    when(ConditionEvaluator.evaluate(any(), any(), any())).thenReturn(true);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition, null, false))
        .isTrue();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void evaluateFromPushPayload() {
    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").build();

    Set<String> changedFiles = pushPayloadChangedFiles();

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(
                WebhookPayloadData.builder().originalEvent(triggerWebhookEvent).changedFiles(changedFiles).build())
            .build();

    TriggerEventDataCondition condition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("spec/manifest1.yml").build();
    assertThat(filter.evaluateFromPushPayload(filterRequestData, condition)).isTrue();

    condition.setOperator(REGEX);
    condition.setValue("(^spec/manifest)[0-9](.yml1$)");
    assertThat(filter.evaluateFromPushPayload(filterRequestData, condition)).isFalse();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void shouldEvaluateOnSCM() {
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPr(PullRequestHook.newBuilder().build()).build();
    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload("").sourceRepoType("Github").build();

    WebhookPayloadData webhookPayloadData = WebhookPayloadData.builder()
                                                .originalEvent(triggerWebhookEvent)
                                                .parseWebhookResponse(parseWebhookResponse)
                                                .build();
    FilterRequestData filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();

    // PR
    assertThat(filter.shouldEvaluateOnSCM(filterRequestData)).isTrue();

    // Push Github , commits < 20
    List<Commit> commits = Arrays.asList(Commit.newBuilder().build(), Commit.newBuilder().build());
    parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().addAllCommits(commits).build()).build();
    webhookPayloadData = WebhookPayloadData.builder()
                             .originalEvent(triggerWebhookEvent)
                             .parseWebhookResponse(parseWebhookResponse)
                             .build();
    filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();
    assertThat(filter.shouldEvaluateOnSCM(filterRequestData)).isFalse();

    // Push gitlab , commits < 20
    parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().addAllCommits(commits).build()).build();
    triggerWebhookEvent.setSourceRepoType("Gitlab");
    webhookPayloadData = WebhookPayloadData.builder()
                             .originalEvent(triggerWebhookEvent)
                             .parseWebhookResponse(parseWebhookResponse)
                             .build();
    filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();
    assertThat(filter.shouldEvaluateOnSCM(filterRequestData)).isFalse();

    // Push Bitbucket , commits < 20
    commits = emptyList();
    parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().addAllCommits(commits).build()).build();
    triggerWebhookEvent.setSourceRepoType("Bitbucket");
    webhookPayloadData = WebhookPayloadData.builder()
                             .originalEvent(triggerWebhookEvent)
                             .parseWebhookResponse(parseWebhookResponse)
                             .build();
    filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();
    assertThat(filter.shouldEvaluateOnSCM(filterRequestData)).isTrue();

    commits = new ArrayList<>();
    Commit commit = Commit.newBuilder().build();
    for (int i = 0; i < 20; i++) {
      commits.add(commit);
    }
    // Push Github , commits > 20
    parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().addAllCommits(commits).build()).build();
    triggerWebhookEvent.setSourceRepoType("github");
    webhookPayloadData = WebhookPayloadData.builder()
                             .originalEvent(triggerWebhookEvent)
                             .parseWebhookResponse(parseWebhookResponse)
                             .build();
    filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();
    assertThat(filter.shouldEvaluateOnSCM(filterRequestData)).isFalse();

    // Push gitlab , commits > 20
    parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().addAllCommits(commits).build()).build();
    triggerWebhookEvent.setSourceRepoType("Gitlab");
    webhookPayloadData = WebhookPayloadData.builder()
                             .originalEvent(triggerWebhookEvent)
                             .parseWebhookResponse(parseWebhookResponse)
                             .build();
    filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();
    assertThat(filter.shouldEvaluateOnSCM(filterRequestData)).isTrue();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetFilesFromPushPayload() {
    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").build();
    Set<String> filesFromPushPayload = ChangedFilesUtils.getFilesFromPushPayload(
        FilterRequestData.builder()
            .webhookPayloadData(WebhookPayloadData.builder().originalEvent(triggerWebhookEvent).build())
            .build(),
        false);

    assertThat(filesFromPushPayload)
        .containsExactlyInAnyOrder("spec/manifest1.yml", "spec/manifest2.yml", "File1_Removed.txt", "File2_Removed.txt",
            "values/value1.yml", "values/value2.yml");
    triggerWebhookEvent.setSourceRepoType("GITHUB");
    assertThat(filesFromPushPayload)
        .containsExactlyInAnyOrder("spec/manifest1.yml", "spec/manifest2.yml", "File1_Removed.txt", "File2_Removed.txt",
            "values/value1.yml", "values/value2.yml");
    triggerWebhookEvent.setSourceRepoType("Gitlab");
    assertThat(filesFromPushPayload)
        .containsExactlyInAnyOrder("spec/manifest1.yml", "spec/manifest2.yml", "File1_Removed.txt", "File2_Removed.txt",
            "values/value1.yml", "values/value2.yml");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void applyFilterTest() throws IOException {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    Long creatAt = 12L;
    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-github-filePath-pr-v2.yaml")),
            StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                            .build())
            .setPush(PushHook.newBuilder().addCommits(Commit.newBuilder().build()).build())
            .build();
    TriggerDetails details1 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(ngTriggerConfigV2)
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").createdAt(creatAt).build();

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(PRWebhookEvent.builder().repository(repository1).build())
                    .originalEvent(triggerWebhookEvent)
                    .parseWebhookResponse(parseWebhookResponse)
                    .changedFiles(pushPayloadChangedFiles())
                    .repository(repository1)
                    .build())
            .pollingResponse(PollingResponse.newBuilder()
                                 .setBuildInfo(BuildInfo.newBuilder()
                                                   .addAllVersions(Collections.singletonList("release.1234"))
                                                   .addAllMetadata(Collections.singletonList(
                                                       Metadata.newBuilder().putAllMetadata(metadata).build()))
                                                   .build())
                                 .build())
            .details(asList(details1))
            .build();
    WebhookEventMappingResponse webhookEventMappingResponse = filter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.getParseWebhookResponse()).isNotNull();
    assertThat(webhookEventMappingResponse.getParseWebhookResponse().hasPush()).isTrue();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void applyFilterNoFileMatchTest() throws IOException {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    Long createdAt = 12L;
    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-github-Invalid-filePath-pr-v2.yaml")),
        StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                            .build())
            .setPush(PushHook.newBuilder().addCommits(Commit.newBuilder().build()).build())
            .build();
    TriggerDetails details1 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(ngTriggerConfigV2)
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").createdAt(createdAt).build();

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(PRWebhookEvent.builder().repository(repository1).build())
                    .originalEvent(triggerWebhookEvent)
                    .parseWebhookResponse(parseWebhookResponse)
                    .repository(repository1)
                    .build())
            .pollingResponse(PollingResponse.newBuilder()
                                 .setBuildInfo(BuildInfo.newBuilder()
                                                   .addAllVersions(Collections.singletonList("release.1234"))
                                                   .addAllMetadata(Collections.singletonList(
                                                       Metadata.newBuilder().putAllMetadata(metadata).build()))
                                                   .build())
                                 .build())
            .details(asList(details1))
            .build();
    WebhookEventMappingResponse webhookEventMappingResponse = filter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.getParseWebhookResponse()).isNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void applyFilterNoPRTest() throws IOException {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    Long createdAt = 12L;
    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-github-filePath-pr-v2.yaml")),
            StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                            .build())
            .build();
    TriggerDetails details1 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(ngTriggerConfigV2)
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").createdAt(createdAt).build();

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(PRWebhookEvent.builder().repository(repository1).build())
                    .originalEvent(triggerWebhookEvent)
                    .parseWebhookResponse(parseWebhookResponse)
                    .repository(repository1)
                    .build())
            .pollingResponse(PollingResponse.newBuilder()
                                 .setBuildInfo(BuildInfo.newBuilder()
                                                   .addAllVersions(Collections.singletonList("release.1234"))
                                                   .addAllMetadata(Collections.singletonList(
                                                       Metadata.newBuilder().putAllMetadata(metadata).build()))
                                                   .build())
                                 .build())
            .details(asList(details1))
            .build();
    WebhookEventMappingResponse webhookEventMappingResponse = filter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.getParseWebhookResponse()).isNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testInitiateManagerTaskAndEvaluateForPRHarness() {
    TriggerDetails triggerDetails = generateTriggerDetails();
    HarnessConnectorDTO harnessConnectorDTO =
        HarnessConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .apiAccess(HarnessApiAccessDTO.builder().spec(HarnessTokenSpecDTO.builder().build()).build())
            .authentication(HarnessAuthenticationDTO.builder()
                                .authType(HTTP)
                                .credentials(HarnessHttpCredentialsDTO.builder()
                                                 .type(HarnessHttpAuthenticationType.USERNAME_AND_TOKEN)
                                                 .httpCredentialsSpec(
                                                     HarnessUsernameTokenDTO.builder().username("usermane").build())
                                                 .build())
                                .build())
            .build();

    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    doReturn(ConnectorDetails.builder()
                 .connectorConfig(harnessConnectorDTO)
                 .connectorType(ConnectorType.HARNESS)
                 .encryptedDataDetails(encryptedDataDetails)
                 .executeOnDelegate(false)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    when(scmFilePathEvaluatorOnManager.execute(any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnManager.getScmPathFilterEvaluationTaskParams(
             any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnManager.getChangedFileset(any(), any(), any()))
        .thenReturn(new HashSet<>(Collections.singletonList("file")));

    when(ConditionEvaluator.evaluate(any(), any(), any())).thenReturn(true);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition, null, false))
        .isTrue();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testInitiateDelegateTaskAndEvaluateForPushHarness() {
    // Init Data
    final String url = "url";
    final String validationRepo = "validationRepo";
    TriggerDetails triggerDetails = generateTriggerDetails();
    HarnessConnectorDTO harnessConnectorDTO =
        HarnessConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .apiAccess(HarnessApiAccessDTO.builder().spec(HarnessTokenSpecDTO.builder().build()).build())
            .authentication(HarnessAuthenticationDTO.builder()
                                .authType(HTTP)
                                .credentials(HarnessHttpCredentialsDTO.builder()
                                                 .type(HarnessHttpAuthenticationType.USERNAME_AND_TOKEN)
                                                 .httpCredentialsSpec(
                                                     HarnessUsernameTokenDTO.builder().username("usermane").build())
                                                 .build())
                                .build())
            .build();

    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPush(PushHook.newBuilder().setBefore("before").setAfter("after").setRef("ref").build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    // Mock apis
    doReturn(ConnectorDetails.builder()
                 .connectorConfig(harnessConnectorDTO)
                 .orgIdentifier("org")
                 .connectorType(ConnectorType.HARNESS)
                 .projectIdentifier("proj")
                 .encryptedDataDetails(encryptedDataDetails)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    when(scmFilePathEvaluatorOnDelegate.getScmPathFilterEvaluationTaskParams(
             any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnDelegate.execute(any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();

    byte[] data = new byte[0];
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().data(data).build());
    doReturn(ScmPathFilterEvaluationTaskResponse.builder().matched(true).build())
        .when(kryoSerializer)
        .asInflatedObject(data);
    doReturn(null).when(taskSetupAbstractionHelper).getOwner(any(), any(), any());

    ArgumentCaptor<DelegateTaskRequest> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    assertThat(filter.initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition, null, false))
        .isTrue();
    verify(taskExecutionUtils, times(1)).executeSyncTask(argumentCaptor.capture());

    // Assert Delegate Task request object generated
    DelegateTaskRequest delegateTaskRequest = argumentCaptor.getValue();
    assertThat(delegateTaskRequest.getAccountId()).isEqualTo("acc");
    assertThat(delegateTaskRequest.getTaskType()).isEqualTo(SCM_PATH_FILTER_EVALUATION_TASK.toString());

    assertThat(delegateTaskRequest.getTaskParameters()).isNotNull();

    TaskParameters taskParameters = delegateTaskRequest.getTaskParameters();
    assertThat(ScmPathFilterEvaluationTaskParams.class.isAssignableFrom(taskParameters.getClass()));
    ScmPathFilterEvaluationTaskParams params = (ScmPathFilterEvaluationTaskParams) taskParameters;
    assertThat(params.getScmConnector()).isEqualTo(harnessConnectorDTO);
    assertThat(params.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
    assertThat(params.getPreviousCommit()).isEqualTo("before");
    assertThat(params.getLatestCommit()).isEqualTo("after");
    assertThat(params.getBranch()).isEqualTo("ref");
    assertThat(params.getOperator()).isEqualTo(EQUALS.getValue());
    assertThat(params.getStandard()).isEqualTo("test");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testApplyFilterWithMultipleTriggersWithoutFilePathCondition() throws IOException {
    when(pmsFeatureFlagHelper.isEnabled(anyString(), eq(PIPE_ALLOW_MULTIPLE_FILEPATH_CONDITIONS))).thenReturn(true);

    List<NGTriggerEntity> ngTriggerEntities = new ArrayList<>();

    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-github-without-filePath-pr-v2.yaml")),
        StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);

    NGTriggerEntity trigger1 = generateNGTriggerEntityWithGithubMetadata("uuid1");
    NGTriggerEntity trigger2 = generateNGTriggerEntityWithGithubMetadata("uuid2");
    ngTriggerEntities.add(trigger1);
    ngTriggerEntities.add(trigger2);

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("accountId")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().sourceRepoType("Github").payload("").build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setPr(PullRequestHook.newBuilder()
                                       .setPr(PullRequest.newBuilder()
                                                  .setNumber(123)
                                                  .setTarget("main")
                                                  .setSource("feature/branch")
                                                  .build())
                                       .setRepo(io.harness.product.ci.scm.proto.Repository.newBuilder()
                                                    .setBranch("main")
                                                    .setClone("http://github.com")
                                                    .build())
                                       .build())
                            .build())
                    .build())
            .details(ngTriggerEntities.stream()
                         .map(entity
                             -> TriggerDetails.builder()
                                    .ngTriggerEntity(entity)
                                    .ngTriggerConfigV2(ngTriggerConfigV2)
                                    .build())
                         .collect(Collectors.toList()))
            .build();

    WebhookEventMappingResponse response = filter.applyFilter(filterRequestData);

    assertThat(response).isNotNull();
    assertThat(response.getTriggers()).hasSize(2); // trigger1 and trigger2 matched because there is no path condition
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testApplyFilterWithMultipleTriggersUseDelegateTaskForPR() throws IOException {
    when(pmsFeatureFlagHelper.isEnabled(anyString(), eq(PIPE_ALLOW_MULTIPLE_FILEPATH_CONDITIONS))).thenReturn(true);

    List<NGTriggerEntity> ngTriggerEntities = new ArrayList<>();

    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-github-single-filePath-pr-v2.yaml")),
        StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);

    NGTriggerEntity trigger1 = generateNGTriggerEntityWithGithubMetadata("uuid1");
    NGTriggerEntity trigger2 = generateNGTriggerEntityWithGithubMetadata("uuid2");
    ngTriggerEntities.add(trigger1);
    ngTriggerEntities.add(trigger2);

    List<TriggerFilepathResponse> scmResponses = Arrays.asList(
        TriggerFilepathResponse.builder()
            .triggerEntityId("uuid1")
            .scmPathFilterEvaluationTaskResponse(ScmPathFilterEvaluationTaskResponse.builder().matched(true).build())
            .build(),
        TriggerFilepathResponse.builder()
            .triggerEntityId("uuid2")
            .scmPathFilterEvaluationTaskResponse(ScmPathFilterEvaluationTaskResponse.builder().matched(false).build())
            .build());

    doReturn(scmResponses)
        .when(scmFilePathEvaluatorOnDelegate)
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(),
            any(ConnectorDetails.class), any(ScmConnector.class));

    when(connectorUtils.getConnectorDetails(any(NGAccess.class), anyString()))
        .thenReturn(ConnectorDetails.builder()
                        .connectorType(ConnectorType.GITHUB)
                        .connectorConfig(GithubConnectorDTO.builder()
                                             .connectionType(GitConnectionType.ACCOUNT)
                                             .url("http://github.com")
                                             .build())
                        .executeOnDelegate(true)
                        .build());

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("accountId")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().sourceRepoType("Github").payload("").build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setPr(PullRequestHook.newBuilder()
                                       .setPr(PullRequest.newBuilder()
                                                  .setNumber(123)
                                                  .setTarget("main")
                                                  .setSource("feature/branch")
                                                  .build())
                                       .setRepo(io.harness.product.ci.scm.proto.Repository.newBuilder()
                                                    .setBranch("main")
                                                    .setClone("http://github.com")
                                                    .build())
                                       .build())
                            .build())
                    .build())
            .details(ngTriggerEntities.stream()
                         .map(entity
                             -> TriggerDetails.builder()
                                    .ngTriggerEntity(entity)
                                    .ngTriggerConfigV2(ngTriggerConfigV2)
                                    .build())
                         .collect(Collectors.toList()))
            .build();

    WebhookEventMappingResponse response = filter.applyFilter(filterRequestData);

    assertThat(response).isNotNull();
    assertThat(response.getTriggers()).hasSize(1); // trigger1 should match only
    assertThat(response.getTriggers()).extracting(trigger -> trigger.getNgTriggerEntity()).contains(trigger1);
    verify(scmFilePathEvaluatorOnDelegate, times(1))
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(), any(), any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testApplyFilterWithMultipleTriggersUseManagerTaskForPR() throws IOException {
    when(pmsFeatureFlagHelper.isEnabled(anyString(), eq(PIPE_ALLOW_MULTIPLE_FILEPATH_CONDITIONS))).thenReturn(true);

    List<NGTriggerEntity> ngTriggerEntities = new ArrayList<>();

    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-github-single-filePath-pr-v2.yaml")),
        StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);

    NGTriggerEntity trigger1 = generateNGTriggerEntityWithGithubMetadata("uuid1");
    NGTriggerEntity trigger2 = generateNGTriggerEntityWithGithubMetadata("uuid2");
    ngTriggerEntities.add(trigger1);
    ngTriggerEntities.add(trigger2);

    List<TriggerFilepathResponse> scmResponses = Arrays.asList(
        TriggerFilepathResponse.builder()
            .triggerEntityId("uuid1")
            .scmPathFilterEvaluationTaskResponse(ScmPathFilterEvaluationTaskResponse.builder().matched(true).build())
            .build(),
        TriggerFilepathResponse.builder()
            .triggerEntityId("uuid2")
            .scmPathFilterEvaluationTaskResponse(ScmPathFilterEvaluationTaskResponse.builder().matched(false).build())
            .build());

    doReturn(scmResponses)
        .when(scmFilePathEvaluatorOnManager)
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(),
            any(ConnectorDetails.class), any(ScmConnector.class));

    when(connectorUtils.getConnectorDetails(any(NGAccess.class), anyString()))
        .thenReturn(ConnectorDetails.builder()
                        .connectorType(ConnectorType.GITHUB)
                        .connectorConfig(GithubConnectorDTO.builder()
                                             .connectionType(GitConnectionType.ACCOUNT)
                                             .url("http://github.com")
                                             .build())
                        .executeOnDelegate(false)
                        .build());

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("accountId")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().sourceRepoType("Github").payload("").build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setPr(PullRequestHook.newBuilder()
                                       .setPr(PullRequest.newBuilder()
                                                  .setNumber(123)
                                                  .setTarget("main")
                                                  .setSource("feature/branch")
                                                  .build())
                                       .setRepo(io.harness.product.ci.scm.proto.Repository.newBuilder()
                                                    .setBranch("main")
                                                    .setClone("http://github.com")
                                                    .build())
                                       .build())
                            .build())
                    .build())
            .details(ngTriggerEntities.stream()
                         .map(entity
                             -> TriggerDetails.builder()
                                    .ngTriggerEntity(entity)
                                    .ngTriggerConfigV2(ngTriggerConfigV2)
                                    .build())
                         .collect(Collectors.toList()))
            .build();

    WebhookEventMappingResponse response = filter.applyFilter(filterRequestData);

    assertThat(response).isNotNull();
    assertThat(response.getTriggers()).hasSize(1); // trigger1 should match only
    assertThat(response.getTriggers()).extracting(TriggerDetails::getNgTriggerEntity).contains(trigger1);
    verify(scmFilePathEvaluatorOnManager, times(1))
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(), any(), any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testApplyFilterWithMultipleTriggersForPush() throws IOException {
    when(pmsFeatureFlagHelper.isEnabled(anyString(), eq(PIPE_ALLOW_MULTIPLE_FILEPATH_CONDITIONS))).thenReturn(true);

    List<NGTriggerEntity> ngTriggerEntities = new ArrayList<>();

    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-github-single-filePath-pr-v2.yaml")),
        StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);

    NGTriggerEntity trigger1 = generateNGTriggerEntityWithGithubMetadata("uuid1");
    NGTriggerEntity trigger2 = generateNGTriggerEntityWithGithubMetadata("uuid2");
    ngTriggerEntities.add(trigger1);
    ngTriggerEntities.add(trigger2);

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("accountId")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().sourceRepoType("Github").payload(pushPayload).build())
                    .changedFiles(pushPayloadChangedFiles())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().build()).build())
                    .build())
            .details(ngTriggerEntities.stream()
                         .map(entity
                             -> TriggerDetails.builder()
                                    .ngTriggerEntity(entity)
                                    .ngTriggerConfigV2(ngTriggerConfigV2)
                                    .build())
                         .collect(Collectors.toList()))
            .build();

    WebhookEventMappingResponse response = filter.applyFilter(filterRequestData);

    assertThat(response).isNotNull();
    assertThat(response.getTriggers()).hasSize(2); // trigger1 and trigger2 should match
    assertThat(response.getTriggers()).extracting(TriggerDetails::getNgTriggerEntity).contains(trigger1, trigger2);
    verify(scmFilePathEvaluatorOnManager, times(0))
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(), any(), any());
    verify(scmFilePathEvaluatorOnDelegate, times(0))
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(), any(), any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testApplyFilterWithMultipleTriggersUsingDelegateTaskForHarnessCodePush() throws IOException {
    when(pmsFeatureFlagHelper.isEnabled(anyString(), eq(PIPE_ALLOW_MULTIPLE_FILEPATH_CONDITIONS))).thenReturn(true);

    List<NGTriggerEntity> ngTriggerEntities = new ArrayList<>();

    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-github-single-filePath-pr-v2.yaml")),
        StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);

    NGTriggerEntity trigger1 = generateNGTriggerEntityWithHarnessCodeMetadata("uuid1");
    NGTriggerEntity trigger2 = generateNGTriggerEntityWithHarnessCodeMetadata("uuid2");
    ngTriggerEntities.add(trigger1);
    ngTriggerEntities.add(trigger2);

    List<TriggerFilepathResponse> scmResponses = Arrays.asList(
        TriggerFilepathResponse.builder()
            .triggerEntityId("uuid1")
            .scmPathFilterEvaluationTaskResponse(ScmPathFilterEvaluationTaskResponse.builder().matched(true).build())
            .build(),
        TriggerFilepathResponse.builder()
            .triggerEntityId("uuid2")
            .scmPathFilterEvaluationTaskResponse(ScmPathFilterEvaluationTaskResponse.builder().matched(false).build())
            .build());

    HarnessConnectorDTO harnessConnectorDTO =
        HarnessConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .apiAccess(HarnessApiAccessDTO.builder().spec(HarnessTokenSpecDTO.builder().build()).build())
            .authentication(HarnessAuthenticationDTO.builder()
                                .authType(HTTP)
                                .credentials(HarnessHttpCredentialsDTO.builder()
                                                 .type(HarnessHttpAuthenticationType.USERNAME_AND_TOKEN)
                                                 .httpCredentialsSpec(
                                                     HarnessUsernameTokenDTO.builder().username("usermane").build())
                                                 .build())
                                .build())
            .build();

    List<EncryptedDataDetail> encryptedDataDetails = emptyList();

    doReturn(scmResponses)
        .when(scmFilePathEvaluatorOnDelegate)
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(),
            any(ConnectorDetails.class), any(ScmConnector.class));

    doReturn(harnessConnectorDTO)
        .when(harnessCodeConnectorUtils)
        .getDummyHarnessCodeConnectorWithJwtAuth(any(), any(), any(), any(), any(), any(), any(), any());

    doReturn(ConnectorDetails.builder()
                 .connectorConfig(harnessConnectorDTO)
                 .orgIdentifier("org")
                 .connectorType(ConnectorType.HARNESS)
                 .projectIdentifier("proj")
                 .encryptedDataDetails(encryptedDataDetails)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), any(ConnectorDTO.class));

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("accountId")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().sourceRepoType("Github").payload("").build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setPr(PullRequestHook.newBuilder()
                                       .setPr(PullRequest.newBuilder()
                                                  .setNumber(123)
                                                  .setTarget("main")
                                                  .setSource("feature/branch")
                                                  .build())
                                       .setRepo(io.harness.product.ci.scm.proto.Repository.newBuilder()
                                                    .setBranch("main")
                                                    .setClone("http://github.com")
                                                    .build())
                                       .build())
                            .build())
                    .build())
            .details(ngTriggerEntities.stream()
                         .map(entity
                             -> TriggerDetails.builder()
                                    .ngTriggerEntity(entity)
                                    .ngTriggerConfigV2(ngTriggerConfigV2)
                                    .build())
                         .collect(Collectors.toList()))
            .build();

    WebhookEventMappingResponse response = filter.applyFilter(filterRequestData);

    assertThat(response).isNotNull();
    assertThat(response.getTriggers()).hasSize(1); // trigger1 should match only
    assertThat(response.getTriggers()).extracting(TriggerDetails::getNgTriggerEntity).contains(trigger1);
    verify(scmFilePathEvaluatorOnDelegate, times(1))
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(), any(), any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEvaluateTriggerConditionsWithUniqueConnectors() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-github-single-filePath-pr-v2.yaml")),
        StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);

    TriggerDetails triggerDetails1 = generateTriggerDetails("uuid1", "connector1");
    triggerDetails1.setNgTriggerConfigV2(ngTriggerConfigV2);
    TriggerDetails triggerDetails2 = generateTriggerDetails("uuid2", "connector2");
    triggerDetails2.setNgTriggerConfigV2(ngTriggerConfigV2);
    TriggerDetails triggerDetails3 = generateTriggerDetails("uuid3", "connector1");
    triggerDetails3.setNgTriggerConfigV2(ngTriggerConfigV2);

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("accountId")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().sourceRepoType("Github").payload("").build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setPr(PullRequestHook.newBuilder()
                                       .setPr(PullRequest.newBuilder()
                                                  .setNumber(123)
                                                  .setTarget("main")
                                                  .setSource("feature/branch")
                                                  .build())
                                       .setRepo(io.harness.product.ci.scm.proto.Repository.newBuilder()
                                                    .setBranch("main")
                                                    .setClone("http://github.com")
                                                    .build())
                                       .build())
                            .build())
                    .build())
            .details(Arrays.asList(triggerDetails1, triggerDetails2, triggerDetails3))
            .build();

    List<TriggerDetails> triggerToEvaluate = new ArrayList<>();
    List<TriggerDetails> matchedTriggers = new ArrayList<>();
    triggerToEvaluate.add(triggerDetails1);
    triggerToEvaluate.add(triggerDetails2);
    triggerToEvaluate.add(triggerDetails3);
    doReturn(null)
        .when(scmFilePathEvaluatorOnManager)
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(),
            any(ConnectorDetails.class), any(ScmConnector.class));

    when(connectorUtils.getConnectorDetails(any(NGAccess.class), anyString()))
        .thenReturn(ConnectorDetails.builder()
                        .connectorType(ConnectorType.GITHUB)
                        .connectorConfig(GithubConnectorDTO.builder()
                                             .connectionType(GitConnectionType.ACCOUNT)
                                             .url("http://github.com")
                                             .build())
                        .executeOnDelegate(false)
                        .build());

    filter.evaluateTriggerConditionsV2(filterRequestData, matchedTriggers, triggerToEvaluate);
    assertThat(matchedTriggers).hasSize(0);
    verify(scmFilePathEvaluatorOnManager, times(2))
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(), any(), any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testProcessAndCollectValidTriggerResponses() {
    TriggerDetails triggerDetails1 = generateTriggerDetails("uuid1", "connector1");
    TriggerDetails triggerDetails2 = generateTriggerDetails("uuid2", "connector2");
    TriggerDetails triggerDetails3 = generateTriggerDetails("uuid3", "connector1");

    List<TriggerDetails> triggerToEvaluate = new ArrayList<>();
    List<TriggerDetails> matchedTriggers = new ArrayList<>();
    List<TriggerFilepathResponse> triggerFilepathResponses = new ArrayList<>();
    triggerToEvaluate.add(triggerDetails1);
    triggerToEvaluate.add(triggerDetails2);
    triggerToEvaluate.add(triggerDetails3);
    triggerFilepathResponses.add(TriggerFilepathResponse.builder().triggerEntityId("unknown").build());
    triggerFilepathResponses.add(TriggerFilepathResponse.builder()
                                     .triggerEntityId(triggerDetails2.getNgTriggerEntity().getUuid())
                                     .scmPathFilterEvaluationTaskResponse(null)
                                     .build());
    triggerFilepathResponses.add(
        TriggerFilepathResponse.builder()
            .triggerEntityId(triggerDetails3.getNgTriggerEntity().getUuid())
            .scmPathFilterEvaluationTaskResponse(
                ScmPathFilterEvaluationTaskResponse.builder().errorMessage("error message").matched(true).build())
            .build());

    filter.processAndCollectValidTriggerResponses(matchedTriggers, triggerFilepathResponses, triggerToEvaluate);
    assertThat(matchedTriggers).hasSize(1);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testApplyFilterWithMultipleTriggersUseDelegateTaskAndDelegateIsNotUpToDate() throws IOException {
    when(pmsFeatureFlagHelper.isEnabled(anyString(), eq(PIPE_ALLOW_MULTIPLE_FILEPATH_CONDITIONS))).thenReturn(true);

    List<NGTriggerEntity> ngTriggerEntities = new ArrayList<>();

    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-github-single-filePath-pr-v2.yaml")),
        StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);

    NGTriggerEntity trigger1 = generateNGTriggerEntityWithGithubMetadata("uuid1");
    NGTriggerEntity trigger2 = generateNGTriggerEntityWithGithubMetadata("uuid2");
    ngTriggerEntities.add(trigger1);
    ngTriggerEntities.add(trigger2);

    doThrow(ScmPathFilterTaskException.class)
        .when(scmFilePathEvaluatorOnDelegate)
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(),
            any(ConnectorDetails.class), any(ScmConnector.class));
    when(scmFilePathEvaluatorOnDelegate.getScmPathFilterEvaluationTaskParams(
             any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnDelegate.execute(any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();
    byte[] data = new byte[0];
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().data(data).usingKryoWithoutReference(true).build());
    doReturn(ScmPathFilterEvaluationTaskResponse.builder().matched(true).build())
        .doReturn(ScmPathFilterEvaluationTaskResponse.builder().matched(false).build())
        .when(referenceFalseKryoSerializer)
        .asInflatedObject(data);
    doReturn(null).when(taskSetupAbstractionHelper).getOwner(any(), any(), any());

    when(connectorUtils.getConnectorDetails(any(NGAccess.class), anyString()))
        .thenReturn(ConnectorDetails.builder()
                        .connectorType(ConnectorType.GITHUB)
                        .connectorConfig(GithubConnectorDTO.builder()
                                             .connectionType(GitConnectionType.ACCOUNT)
                                             .url("http://github.com")
                                             .build())
                        .executeOnDelegate(true)
                        .build());

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("accountId")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().sourceRepoType("Github").payload("").build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setPr(PullRequestHook.newBuilder()
                                       .setPr(PullRequest.newBuilder()
                                                  .setNumber(123)
                                                  .setTarget("main")
                                                  .setSource("feature/branch")
                                                  .build())
                                       .setRepo(io.harness.product.ci.scm.proto.Repository.newBuilder()
                                                    .setBranch("main")
                                                    .setClone("http://github.com")
                                                    .build())
                                       .build())
                            .build())
                    .build())
            .details(ngTriggerEntities.stream()
                         .map(entity
                             -> TriggerDetails.builder()
                                    .ngTriggerEntity(entity)
                                    .ngTriggerConfigV2(ngTriggerConfigV2)
                                    .build())
                         .collect(Collectors.toList()))
            .build();

    WebhookEventMappingResponse response = filter.applyFilter(filterRequestData);

    assertThat(response).isNotNull();
    assertThat(response.getTriggers()).hasSize(1); // trigger1 should match only
    assertThat(response.getTriggers()).extracting(trigger -> trigger.getNgTriggerEntity()).contains(trigger1);
    verify(scmFilePathEvaluatorOnDelegate, times(1))
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(), any(), any());
    verify(scmFilePathEvaluatorOnDelegate, times(2))
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), any(), any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void
  testApplyFilterWithMultipleTriggersUseDelegateTaskAndDelegateIsNotUpToDate_doNotRepeatTriggerWithoutFilePath()
      throws IOException {
    when(pmsFeatureFlagHelper.isEnabled(anyString(), eq(PIPE_ALLOW_MULTIPLE_FILEPATH_CONDITIONS))).thenReturn(true);

    List<TriggerDetails> triggerDetails = new ArrayList<>();

    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-github-single-filePath-pr-v2.yaml")),
        StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);

    String ngTriggerYaml_github_pr_without_filePath = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-github-without-filePath-pr-v2.yaml")),
        StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2WithoutFilePath =
        ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr_without_filePath);

    NGTriggerEntity trigger1 = generateNGTriggerEntityWithGithubMetadata("uuid1");
    NGTriggerEntity trigger2 = generateNGTriggerEntityWithGithubMetadata("uuid2");
    NGTriggerEntity trigger3 = generateNGTriggerEntityWithGithubMetadata("uuid3");
    triggerDetails.add(TriggerDetails.builder().ngTriggerEntity(trigger1).ngTriggerConfigV2(ngTriggerConfigV2).build());
    triggerDetails.add(TriggerDetails.builder().ngTriggerEntity(trigger2).ngTriggerConfigV2(ngTriggerConfigV2).build());
    triggerDetails.add(
        TriggerDetails.builder().ngTriggerEntity(trigger3).ngTriggerConfigV2(ngTriggerConfigV2WithoutFilePath).build());

    doThrow(ScmPathFilterTaskException.class)
        .when(scmFilePathEvaluatorOnDelegate)
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(),
            any(ConnectorDetails.class), any(ScmConnector.class));
    when(scmFilePathEvaluatorOnDelegate.getScmPathFilterEvaluationTaskParams(
             any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();
    when(scmFilePathEvaluatorOnDelegate.execute(any(), (TriggerEventDataCondition) any(), any(), any()))
        .thenCallRealMethod();
    byte[] data = new byte[0];
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().data(data).usingKryoWithoutReference(true).build());
    doReturn(ScmPathFilterEvaluationTaskResponse.builder().matched(true).build())
        .doReturn(ScmPathFilterEvaluationTaskResponse.builder().matched(false).build())
        .when(referenceFalseKryoSerializer)
        .asInflatedObject(data);
    doReturn(null).when(taskSetupAbstractionHelper).getOwner(any(), any(), any());

    when(connectorUtils.getConnectorDetails(any(NGAccess.class), anyString()))
        .thenReturn(ConnectorDetails.builder()
                        .connectorType(ConnectorType.GITHUB)
                        .connectorConfig(GithubConnectorDTO.builder()
                                             .connectionType(GitConnectionType.ACCOUNT)
                                             .url("http://github.com")
                                             .build())
                        .executeOnDelegate(true)
                        .build());

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("accountId")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().sourceRepoType("Github").payload("").build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setPr(PullRequestHook.newBuilder()
                                       .setPr(PullRequest.newBuilder()
                                                  .setNumber(123)
                                                  .setTarget("main")
                                                  .setSource("feature/branch")
                                                  .build())
                                       .setRepo(io.harness.product.ci.scm.proto.Repository.newBuilder()
                                                    .setBranch("main")
                                                    .setClone("http://github.com")
                                                    .build())
                                       .build())
                            .build())
                    .build())
            .details(triggerDetails)
            .build();

    WebhookEventMappingResponse response = filter.applyFilter(filterRequestData);

    assertThat(response).isNotNull();
    assertThat(response.getTriggers()).hasSize(2); // trigger1 and trigger3 should match
    assertThat(response.getTriggers()).extracting(trigger -> trigger.getNgTriggerEntity()).contains(trigger1, trigger3);
    verify(scmFilePathEvaluatorOnDelegate, times(1))
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(), any(), any());
    verify(scmFilePathEvaluatorOnDelegate, times(2))
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), any(), any());
  }

  private NGTriggerEntity generateNGTriggerEntityWithGithubMetadata(String uuid) {
    return NGTriggerEntity.builder()
        .uuid(uuid)
        .accountId("account")
        .orgIdentifier("org")
        .projectIdentifier("project")
        .parentUniqueId("uniqueId-" + uuid)
        .enabled(true)
        .metadata(NGTriggerMetadata.builder()
                      .webhook(WebhookMetadata.builder()
                                   .type("GITHUB")
                                   .git(GitMetadata.builder().connectorIdentifier("conn").build())
                                   .build())
                      .build())
        .build();
  }

  private NGTriggerEntity generateNGTriggerEntityWithHarnessCodeMetadata(String uuid) {
    return NGTriggerEntity.builder()
        .uuid(uuid)
        .accountId("account")
        .orgIdentifier("org")
        .projectIdentifier("project")
        .parentUniqueId("uniqueId-" + uuid)
        .enabled(true)
        .metadata(NGTriggerMetadata.builder()
                      .webhook(WebhookMetadata.builder()
                                   .type("GITHUB")
                                   .git(GitMetadata.builder().isHarnessScm(true).connectorIdentifier("conn").build())
                                   .build())
                      .build())
        .build();
  }

  private TriggerDetails generateTriggerDetails(String uuid, String connectorIdentifier) {
    NGTriggerEntity ngTriggerEntity =
        NGTriggerEntity.builder()
            .uuid(uuid)
            .identifier("id")
            .accountId("acc")
            .orgIdentifier("org")
            .projectIdentifier("proj")
            .parentUniqueId("uniqueId-" + uuid)
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder()
                                       .git(GitMetadata.builder().connectorIdentifier(connectorIdentifier).build())
                                       .build())
                          .build())
            .build();
    return TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
  }

  private TriggerDetails generateTriggerDetailsWithHarnessCode(String uuid) {
    NGTriggerEntity ngTriggerEntity =
        NGTriggerEntity.builder()
            .uuid(uuid)
            .identifier("id")
            .accountId("acc")
            .orgIdentifier("org")
            .projectIdentifier("proj")
            .parentUniqueId("uniqueId-" + uuid)
            .metadata(
                NGTriggerMetadata.builder()
                    .webhook(WebhookMetadata.builder().git(GitMetadata.builder().isHarnessScm(true).build()).build())
                    .build())
            .build();
    return TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEvaluateTriggerConditionsWhenConnectorsAreEmpty() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-github-single-filePath-pr-v2.yaml")),
        StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);

    TriggerDetails triggerDetails1 = generateTriggerDetailsWithHarnessCode("uuid1");
    triggerDetails1.setNgTriggerConfigV2(ngTriggerConfigV2);
    TriggerDetails triggerDetails2 = generateTriggerDetailsWithHarnessCode("uuid2");
    triggerDetails2.setNgTriggerConfigV2(ngTriggerConfigV2);
    TriggerDetails triggerDetails3 = generateTriggerDetailsWithHarnessCode("uuid3");
    triggerDetails3.setNgTriggerConfigV2(ngTriggerConfigV2);

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("accountId")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().sourceRepoType("Harness").payload("").build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setPr(PullRequestHook.newBuilder()
                                       .setPr(PullRequest.newBuilder()
                                                  .setNumber(123)
                                                  .setTarget("main")
                                                  .setSource("feature/branch")
                                                  .build())
                                       .setRepo(io.harness.product.ci.scm.proto.Repository.newBuilder()
                                                    .setBranch("main")
                                                    .setClone("http://github.com")
                                                    .build())
                                       .build())
                            .build())
                    .build())
            .details(Arrays.asList(triggerDetails1, triggerDetails2, triggerDetails3))
            .build();

    List<TriggerDetails> triggerToEvaluate = new ArrayList<>();
    List<TriggerDetails> matchedTriggers = new ArrayList<>();
    triggerToEvaluate.add(triggerDetails1);
    triggerToEvaluate.add(triggerDetails2);
    triggerToEvaluate.add(triggerDetails3);
    doReturn(null)
        .when(scmFilePathEvaluatorOnManager)
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(),
            any(ConnectorDetails.class), any(ScmConnector.class));

    when(connectorUtils.getConnectorDetails(any(NGAccess.class), any(ConnectorDTO.class)))
        .thenReturn(ConnectorDetails.builder()
                        .connectorType(ConnectorType.HARNESS)
                        .connectorConfig(HarnessConnectorDTO.builder()
                                             .connectionType(GitConnectionType.ACCOUNT)
                                             .url("http://harnesscode.com")
                                             .build())
                        .executeOnDelegate(false)
                        .build());

    filter.evaluateTriggerConditionsV2(filterRequestData, matchedTriggers, triggerToEvaluate);
    assertThat(matchedTriggers).hasSize(0);
    verify(scmFilePathEvaluatorOnManager, times(1))
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(), any(), any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testEvaluateTriggerConditionsWithMixedHarnessAndGithubConnectors() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-github-single-filePath-pr-v2.yaml")),
        StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);

    // Mix: Harness Code triggers (blank connectorIdentifier) and GitHub triggers (non-blank)
    TriggerDetails harnessTrigger1 = generateTriggerDetailsWithHarnessCode("uuid-h1");
    harnessTrigger1.setNgTriggerConfigV2(ngTriggerConfigV2);
    TriggerDetails githubTrigger = generateTriggerDetails("uuid-g1", "account.conn");
    githubTrigger.setNgTriggerConfigV2(ngTriggerConfigV2);
    TriggerDetails harnessTrigger2 = generateTriggerDetailsWithHarnessCode("uuid-h2");
    harnessTrigger2.setNgTriggerConfigV2(ngTriggerConfigV2);

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("accountId")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().sourceRepoType("Harness").payload("").build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setPr(PullRequestHook.newBuilder()
                                       .setPr(PullRequest.newBuilder()
                                                  .setNumber(1)
                                                  .setTarget("main")
                                                  .setSource("feature/branch")
                                                  .build())
                                       .setRepo(io.harness.product.ci.scm.proto.Repository.newBuilder()
                                                    .setBranch("main")
                                                    .setClone("http://example.com")
                                                    .build())
                                       .build())
                            .build())
                    .build())
            .details(Arrays.asList(harnessTrigger1, githubTrigger, harnessTrigger2))
            .build();

    List<TriggerDetails> triggerToEvaluate = new ArrayList<>();
    List<TriggerDetails> matchedTriggers = new ArrayList<>();
    triggerToEvaluate.add(harnessTrigger1);
    triggerToEvaluate.add(githubTrigger);
    triggerToEvaluate.add(harnessTrigger2);

    doReturn(null)
        .when(scmFilePathEvaluatorOnManager)
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(),
            any(ConnectorDetails.class), any(ScmConnector.class));

    when(connectorUtils.getConnectorDetails(any(NGAccess.class), anyString()))
        .thenReturn(ConnectorDetails.builder()
                        .connectorType(ConnectorType.GITHUB)
                        .connectorConfig(GithubConnectorDTO.builder()
                                             .connectionType(GitConnectionType.ACCOUNT)
                                             .url("http://github.com")
                                             .build())
                        .executeOnDelegate(false)
                        .build());

    when(connectorUtils.getConnectorDetails(any(NGAccess.class), any(ConnectorDTO.class)))
        .thenReturn(ConnectorDetails.builder()
                        .connectorType(ConnectorType.HARNESS)
                        .connectorConfig(HarnessConnectorDTO.builder()
                                             .connectionType(GitConnectionType.ACCOUNT)
                                             .url("http://harnesscode.com")
                                             .build())
                        .executeOnDelegate(false)
                        .build());

    filter.evaluateTriggerConditionsV2(filterRequestData, matchedTriggers, triggerToEvaluate);
    assertThat(matchedTriggers).hasSize(0);
    verify(scmFilePathEvaluatorOnManager, times(2))
        .execute(any(FilterRequestData.class), any(TriggerEventDataCondition.class), anyList(), any(), any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testGetFilesFromPushPayloadWithEmptyCommitsReturnsEmpty() {
    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayloadWithNoCommits).sourceRepoType("Github").build();
    Set<String> filesFromPushPayload = ChangedFilesUtils.getFilesFromPushPayload(
        FilterRequestData.builder()
            .webhookPayloadData(WebhookPayloadData.builder().originalEvent(triggerWebhookEvent).build())
            .build(),
        false);

    assertThat(filesFromPushPayload).isEmpty();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testGetFilesFromPushPayloadWithEmptyCommitsFallbackToHeadCommit() {
    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayloadWithNoCommits).sourceRepoType("Github").build();
    Set<String> filesFromPushPayload = ChangedFilesUtils.getFilesFromPushPayload(
        FilterRequestData.builder()
            .webhookPayloadData(WebhookPayloadData.builder().originalEvent(triggerWebhookEvent).build())
            .build(),
        true);

    assertThat(filesFromPushPayload)
        .containsExactlyInAnyOrder("spec/manifest2.yml", "File2_Removed.txt", "values/value2.yml");
  }
}
