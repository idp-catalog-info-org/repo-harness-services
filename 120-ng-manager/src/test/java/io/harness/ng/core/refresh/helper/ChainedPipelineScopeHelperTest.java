/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.refresh.helper;

import static io.harness.rule.OwnerRule.THRISHANK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.refresh.bean.EntityRefreshContext;
import io.harness.persistence.PersistentEntity;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class ChainedPipelineScopeHelperTest extends CategoryTest {
  private final ChainedPipelineScopeHelper helper = new ChainedPipelineScopeHelper();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testIsChainedPipelineStage() {
    ObjectNode pipelineStage = mapper.createObjectNode();
    pipelineStage.put("type", "Pipeline");
    assertThat(helper.isChainedPipelineStage("stage", pipelineStage)).isTrue();

    ObjectNode deploymentStage = mapper.createObjectNode();
    deploymentStage.put("type", "Deployment");
    assertThat(helper.isChainedPipelineStage("stage", deploymentStage)).isFalse();
    assertThat(helper.isChainedPipelineStage("service", pipelineStage)).isFalse();
    assertThat(helper.isChainedPipelineStage("stage", null)).isFalse();
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testChildScopedContextCopiesCacheAndSwitchesOrgProject() {
    Map<String, PersistentEntity> cacheMap = new HashMap<>();
    EntityRefreshContext parent = EntityRefreshContext.builder()
                                      .accountId("accountId")
                                      .orgId("LNX")
                                      .projectId("parentProject")
                                      .cacheMap(cacheMap)
                                      .build();
    ObjectNode stage = mapper.createObjectNode();
    stage.put("type", "Pipeline");
    ObjectNode spec = mapper.createObjectNode();
    spec.put("org", "WIN");
    spec.put("project", "WIN_1384");
    spec.put("pipeline", "test");
    stage.set("spec", spec);

    EntityRefreshContext child = helper.childScopedContext(stage, parent);
    assertThat(child).isNotNull();
    assertThat(child.getAccountId()).isEqualTo("accountId");
    assertThat(child.getOrgId()).isEqualTo("WIN");
    assertThat(child.getProjectId()).isEqualTo("WIN_1384");
    assertThat(child.getCacheMap()).isSameAs(cacheMap);
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testChildScopedContextReturnsNullWhenScopeIsNotFixed() {
    EntityRefreshContext parent =
        EntityRefreshContext.builder().accountId("accountId").orgId("LNX").projectId("parentProject").build();

    ObjectNode runtimeOrg = pipelineStage("<+input>", "WIN_1384");
    assertThat(helper.childScopedContext(runtimeOrg, parent)).isNull();

    ObjectNode expressionOrg = pipelineStage("<+org>", "WIN_1384");
    assertThat(helper.childScopedContext(expressionOrg, parent)).isNull();

    ObjectNode missingSpec = mapper.createObjectNode();
    missingSpec.put("type", "Pipeline");
    assertThat(helper.childScopedContext(missingSpec, parent)).isNull();
  }

  private ObjectNode pipelineStage(String org, String project) {
    ObjectNode stage = mapper.createObjectNode();
    stage.put("type", "Pipeline");
    ObjectNode spec = mapper.createObjectNode();
    spec.put("org", org);
    spec.put("project", project);
    stage.set("spec", spec);
    return stage;
  }
}
