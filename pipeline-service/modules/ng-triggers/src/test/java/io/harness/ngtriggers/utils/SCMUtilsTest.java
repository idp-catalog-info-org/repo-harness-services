/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.rule.OwnerRule.VINICIUS;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.delegate.task.scm.GitRefType;
import io.harness.delegate.task.scm.ScmGitRefTaskResponseData;
import io.harness.encryption.SecretRefData;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.FindCommitResponse;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.rule.Owner;
import io.harness.secrets.SecretDecryptor;
import io.harness.serializer.KryoSerializer;
import io.harness.service.ScmServiceClient;
import io.harness.tasks.BinaryResponseData;
import io.harness.utils.ConnectorUtils;
import io.harness.utils.ScopeResolutionHelper;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class SCMUtilsTest extends CategoryTest {
  private static final String PARENT_UNIQUE_ID = "uniqueId";

  @Mock private SecretDecryptor secretDecryptor;
  @Mock private SCMGrpc.SCMBlockingStub scmBlockingStub;
  @Mock private ScmServiceClient scmServiceClient;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private TaskExecutionUtils taskExecutionUtils;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private SCMDataObtainer scmDataObtainer;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;
  @InjectMocks private SCMUtils scmUtils;

  private TriggerDetails triggerDetails;
  private FilterRequestData filterRequestData;
  private static final String expectedCommitMessage = "myMessage";

  @Before
  public void setUp() throws IOException {
    initMocks(this);
    triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .parentUniqueId(PARENT_UNIQUE_ID)
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .build();
    filterRequestData = FilterRequestData.builder().details(List.of(triggerDetails)).accountId("acc").build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier("acc")
                              .orgIdentifier("org")
                              .projectIdentifier("proj")
                              .uniqueId(PARENT_UNIQUE_ID)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    doReturn(singletonMap(PARENT_UNIQUE_ID, Optional.of(scopeInfo)))
        .when(scopeResolutionHelper)
        .getScopeInfos(anyString(), anyList());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testFetchCommitMessageViaManager() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("url")
                                 .apiAccess(GithubApiAccessDTO.builder()
                                                .type(GithubApiAccessType.TOKEN)
                                                .spec(GithubTokenSpecDTO.builder()
                                                          .tokenRef(SecretRefData.builder().identifier("token").build())
                                                          .build())
                                                .build())
                                 .build())
            .executeOnDelegate(false)
            .build();
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);

    FindCommitResponse findCommitResponse =
        FindCommitResponse.newBuilder()
            .setCommit(Commit.newBuilder().setMessage(expectedCommitMessage).build())
            .build();
    when(scmServiceClient.findCommit(any(), any(), any())).thenReturn(findCommitResponse);
    String commitMessage = scmUtils.fetchCommitMessage("commitId", filterRequestData);
    assertThat(commitMessage).isEqualTo(expectedCommitMessage);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testFetchCommitMessageViaDelegate() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("url")
                                 .apiAccess(GithubApiAccessDTO.builder()
                                                .type(GithubApiAccessType.TOKEN)
                                                .spec(GithubTokenSpecDTO.builder()
                                                          .tokenRef(SecretRefData.builder().identifier("token").build())
                                                          .build())
                                                .build())
                                 .build())
            .executeOnDelegate(true)
            .build();
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);

    byte[] data = new byte[0];
    doReturn(BinaryResponseData.builder().data(data).build()).when(taskExecutionUtils).executeSyncTask(any());

    FindCommitResponse findCommitResponse =
        FindCommitResponse.newBuilder()
            .setCommit(Commit.newBuilder().setMessage(expectedCommitMessage).build())
            .build();
    doReturn(ScmGitRefTaskResponseData.builder()
                 .gitRefType(GitRefType.FIND_COMMIT)
                 .findCommitResponse(findCommitResponse.toByteArray())
                 .build())
        .when(kryoSerializer)
        .asInflatedObject(any());
    when(scmServiceClient.findCommit(any(), any(), any())).thenReturn(findCommitResponse);
    String commitMessage = scmUtils.fetchCommitMessage("commitId", filterRequestData);
    assertThat(commitMessage).isEqualTo(expectedCommitMessage);
  }
}
