/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.blockExecutionMetadata;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.execution.BlockExecutionMetadata;
import io.harness.repositories.blockexecution.BlockExecutionMetadataRepository;
import io.harness.rule.Owner;
import io.harness.utils.ScopeResolutionHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class BlockExecutionMetadataServiceImplTest extends OrchestrationTestBase {
  @InjectMocks private BlockExecutionMetadataServiceImpl blockExecutionMetadataService;
  @Mock BlockExecutionMetadataRepository blockExecutionMetadataRepository;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  String accountId = "accountId";
  String orgId = "orgId";
  String projectUniqueId = "projectUniqueId";
  String orgUniqueId = "orgUniqueId";
  String accountUniqueId = "accountUniqueId";

  @Before
  public void beforeTest() throws ExecutionException {
    MockitoAnnotations.initMocks(this);
    when(blockExecutionMetadataRepository.existsByAccountId(anyString())).thenReturn(true);
    when(scopeResolutionHelper.getUniqueIdsIncludingParentScopes(any(ScopeInfo.class))).thenAnswer(invocation -> {
      ScopeInfo scopeInfo = invocation.getArgument(0);
      String uniqueId = scopeInfo.getUniqueId();
      // Return different parent scopes based on the uniqueId
      if ("projectUniqueId".equals(uniqueId)) {
        return Map.of(ScopeLevel.ACCOUNT, accountUniqueId, ScopeLevel.ORGANIZATION, orgUniqueId, ScopeLevel.PROJECT,
            projectUniqueId);
      } else {
        // For "otherUniqueId", return a different set of parent scopes that don't overlap
        return Map.of(ScopeLevel.ACCOUNT, "otherAccountUniqueId", ScopeLevel.ORGANIZATION, "otherOrgUniqueId",
            ScopeLevel.PROJECT, "otherUniqueId");
      }
    });
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void shouldTestSave() {
    List<BlockExecutionMetadata> blockExecutionMetadataList = new ArrayList<>();
    blockExecutionMetadataList.add(
        BlockExecutionMetadata.builder().accountId(accountId).parentUniqueId(projectUniqueId).build());
    blockExecutionMetadataList.add(BlockExecutionMetadata.builder()
                                       .accountId(accountId)
                                       .parentUniqueId(projectUniqueId)
                                       .pipelineId("pipeline2")
                                       .build());
    blockExecutionMetadataList.add(
        BlockExecutionMetadata.builder().accountId(accountId).parentUniqueId(projectUniqueId).build());
    blockExecutionMetadataList.add(
        BlockExecutionMetadata.builder().accountId(accountId).parentUniqueId(orgUniqueId).build());
    when(blockExecutionMetadataRepository.findAll(anyString())).thenReturn(blockExecutionMetadataList);
    boolean shouldBlock =
        blockExecutionMetadataService.shouldBlock(accountId, orgId, "project2", "pipeline2", projectUniqueId);
    assertThat(shouldBlock).isTrue();

    shouldBlock = blockExecutionMetadataService.shouldBlock(accountId, orgId, "project2", "pipeline3", projectUniqueId);
    assertThat(shouldBlock).isTrue();

    shouldBlock = blockExecutionMetadataService.shouldBlock(accountId, orgId, "project3", "pipelinex", projectUniqueId);
    assertThat(shouldBlock).isTrue();

    shouldBlock =
        blockExecutionMetadataService.shouldBlock(accountId, "orgId2", "project3", "pipelinex", projectUniqueId);
    assertThat(shouldBlock).isTrue();

    shouldBlock =
        blockExecutionMetadataService.shouldBlock(accountId, "orgId3", "project3", "pipelinex", "otherUniqueId");
    assertThat(shouldBlock).isFalse();

    shouldBlock =
        blockExecutionMetadataService.shouldBlock(accountId, orgId, "projectxx", "pipeline3", "otherUniqueId");
    assertThat(shouldBlock).isFalse();

    List<BlockExecutionMetadata> blockExecutionMetadataList2 = new ArrayList<>();
    blockExecutionMetadataList2.add(
        BlockExecutionMetadata.builder().accountId("accountx").parentUniqueId(accountUniqueId).build());
    when(blockExecutionMetadataRepository.findAll(anyString())).thenReturn(blockExecutionMetadataList2);
    shouldBlock =
        blockExecutionMetadataService.shouldBlock("accountx", orgId, "projectxx", "pipeline3", projectUniqueId);
    assertThat(shouldBlock).isTrue();
  }
}
