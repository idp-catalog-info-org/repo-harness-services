/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.util;

import static io.harness.rule.OwnerRule.SARTHAK_KASAT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.BambooArtifactConfig;
import io.harness.cdng.artifact.resources.bamboo.BambooResourceService;
import io.harness.cdng.artifact.resources.bamboo.dtos.BambooPlanKeysDTO;
import io.harness.evaluators.CDYamlExpressionEvaluator;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import junitparams.JUnitParamsRunner;
import org.jooq.tools.reflect.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
@RunWith(JUnitParamsRunner.class)
public class BambooResourceUtilsTest extends CategoryTest {
  @InjectMocks private BambooResourceUtils bambooResourceUtils;
  @Inject private ArtifactResourceUtils spyArtifactResourceUtils;
  @Mock private BambooResourceService bambooResourceService;
  @Mock private CDYamlExpressionEvaluator cdYamlExpressionEvaluator;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  BambooArtifactConfig bambooArtifactConfig =
      BambooArtifactConfig.builder()
          .connectorRef(ParameterField.<String>builder().value("connectorref").build())
          .build();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    spyArtifactResourceUtils = spy(ArtifactResourceUtils.class);
    doNothing().when(spyArtifactResourceUtils).checkConnectorAccess(any());
    Reflect.on(bambooResourceUtils).set("artifactResourceUtils", spyArtifactResourceUtils);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetBambooPlanKeys() {
    BambooPlanKeysDTO planKeys = BambooPlanKeysDTO.builder().build();

    Map<String, String> contextMap = new HashMap<>();
    contextMap.put("serviceGitBranch", "main-patch");

    YamlExpressionEvaluatorWithContext yamlExpressionEvaluatorWithContext =
        YamlExpressionEvaluatorWithContext.builder()
            .yamlExpressionEvaluator(cdYamlExpressionEvaluator)
            .contextMap(contextMap)
            .build();
    doReturn(yamlExpressionEvaluatorWithContext)
        .when(spyArtifactResourceUtils)
        .getYamlExpressionEvaluatorWithContext(any(), any(), any(), any(), any(), any(), any(), any());

    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef("connectorref", "accountId", "orgId", "projectId");

    doReturn(true).when(spyArtifactResourceUtils).isRemoteService(any(), any(), any(), any());

    doReturn(bambooArtifactConfig)
        .when(spyArtifactResourceUtils)
        .locateArtifactInService(any(), any(), any(), any(), any(), eq("main-patch"));

    doReturn(planKeys).when(bambooResourceService).getPlanName(identifierRef, "orgId", "projectId", null);

    assertThat(bambooResourceUtils.getBambooPlanKeys(
                   "connectorref", "accountId", "orgId", "projectId", "pipeId", null, "", "serviceref", ""))
        .isEqualTo(planKeys);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetBambooArtifactPaths() {
    List<String> artifactPaths = new ArrayList<>();

    Map<String, String> contextMap = new HashMap<>();
    contextMap.put("serviceGitBranch", "main-patch");

    YamlExpressionEvaluatorWithContext yamlExpressionEvaluatorWithContext =
        YamlExpressionEvaluatorWithContext.builder()
            .yamlExpressionEvaluator(cdYamlExpressionEvaluator)
            .contextMap(contextMap)
            .build();

    doReturn(yamlExpressionEvaluatorWithContext)
        .when(spyArtifactResourceUtils)
        .getYamlExpressionEvaluatorWithContext(any(), any(), any(), any(), any(), any(), any(), any());

    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef("connectorref", "accountId", "orgId", "projectId");

    doReturn(true).when(spyArtifactResourceUtils).isRemoteService(any(), any(), any(), any());

    doReturn(bambooArtifactConfig)
        .when(spyArtifactResourceUtils)
        .locateArtifactInService(any(), any(), any(), any(), any(), eq("main-patch"));

    doReturn(artifactPaths)
        .when(bambooResourceService)
        .getArtifactPath(identifierRef, "orgId", "projectId", "plan", null);

    assertThat(bambooResourceUtils.getBambooArtifactPaths(
                   "connectorref", "accountId", "orgId", "projectId", "pipeId", "plan", null, "serviceref", "", ""))
        .isEqualTo(artifactPaths);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetBambooArtifactBuildDetails() {
    List<BuildDetails> buildDetails = new ArrayList<>();

    Map<String, String> contextMap = new HashMap<>();
    contextMap.put("serviceGitBranch", "main-patch");

    YamlExpressionEvaluatorWithContext yamlExpressionEvaluatorWithContext =
        YamlExpressionEvaluatorWithContext.builder()
            .yamlExpressionEvaluator(cdYamlExpressionEvaluator)
            .contextMap(contextMap)
            .build();

    doReturn(yamlExpressionEvaluatorWithContext)
        .when(spyArtifactResourceUtils)
        .getYamlExpressionEvaluatorWithContext(any(), any(), any(), any(), any(), any(), any(), any());

    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef("connectorref", "accountId", "orgId", "projectId");

    doReturn(true).when(spyArtifactResourceUtils).isRemoteService(any(), any(), any(), any());

    doReturn(bambooArtifactConfig)
        .when(spyArtifactResourceUtils)
        .locateArtifactInService(any(), any(), any(), any(), any(), eq("main-patch"));

    doReturn(buildDetails)
        .when(bambooResourceService)
        .getBuilds(identifierRef, "orgId", "projectId", "plan", Collections.EMPTY_LIST, null);

    assertThat(bambooResourceUtils.getBambooArtifactBuildDetails("connectorref", "accountId", "orgId", "projectId",
                   "pipeId", "plan", Collections.EMPTY_LIST, null, "serviceref", "", ""))
        .isEqualTo(buildDetails);
  }
}
