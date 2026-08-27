/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.filter;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.utils.PipelineGitXHelper;

import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

@OwnedBy(HarnessTeam.PIPELINE)
@PrepareForTest({PipelineGitXHelper.class})
public class AsyncFilterCreationDispatcherTest extends CategoryTest {
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @Mock ScopeInfoClient scopeInfoClient;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testFilterCreation() throws Exception {
    MockedStatic<PipelineGitXHelper> mockSettings = Mockito.mockStatic(PipelineGitXHelper.class);
    MockedStatic<NGRestUtils> ngRestUtilsMock = Mockito.mockStatic(NGRestUtils.class);
    AsyncFilterCreationDispatcher dispatcher = AsyncFilterCreationDispatcher.builder()
                                                   .pmsPipelineService(pmsPipelineService)
                                                   .pmsPipelineServiceHelper(pmsPipelineServiceHelper)
                                                   .scopeInfoClient(scopeInfoClient)
                                                   .yamlHash(101)
                                                   .uuid("uuid")
                                                   .messageId("messageId")
                                                   .build();
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId("ACCOUNT_ID")
                                        .orgIdentifier("ORG_IDENTIFIER")
                                        .projectIdentifier("PROJ_IDENTIFIER")
                                        .identifier("PIPELINE_IDENTIFIER")
                                        .name("PIPELINE_IDENTIFIER")
                                        .yaml("yaml")
                                        .stageCount(0)
                                        .stageName("STAGE")
                                        .version(1L)
                                        .allowStageExecutions(false)
                                        .connectorRef("connectorRef")
                                        .repo("repo")
                                        .yamlHash(101)
                                        .parentUniqueId("PARENT_UNIQUE_ID")
                                        .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier("ACCOUNT_ID")
                              .orgIdentifier("ORG_IDENTIFIER")
                              .projectIdentifier("PROJ_IDENTIFIER")
                              .uniqueId("PARENT_UNIQUE_ID")
                              .build();
    when(pmsPipelineService.getPipelineByUUID(any())).thenReturn(Optional.of(pipelineEntity));
    when(pmsPipelineServiceHelper.updatePipelineInfo(any(), any(), any(), anyBoolean())).thenReturn(pipelineEntity);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any()))
        .thenReturn(Map.of("PARENT_UNIQUE_ID", Optional.of(scopeInfo)));
    dispatcher.run();
    mockSettings.verify(()
                            -> PipelineGitXHelper.setupGitParentEntityDetails(eq("ACCOUNT_ID"), eq("ORG_IDENTIFIER"),
                                eq("PROJ_IDENTIFIER"), eq("connectorRef"), eq("repo")));
    mockSettings.close();
    ngRestUtilsMock.close();
  }
}