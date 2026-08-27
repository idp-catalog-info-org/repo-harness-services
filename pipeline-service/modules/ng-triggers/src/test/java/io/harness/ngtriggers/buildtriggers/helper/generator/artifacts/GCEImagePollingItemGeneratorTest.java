/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helper.generator.artifacts;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ABHIPRANAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.PollingConfig;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.buildtriggers.helper.generator.PollingItemGeneratorTestHelper;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GCEImagePollingItemGenerator;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.polling.contracts.PollingItem;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class GCEImagePollingItemGeneratorTest extends CategoryTest {
  BuildTriggerHelper buildTriggerHelper = new BuildTriggerHelper(null);
  @InjectMocks NGTriggerElementMapper ngTriggerElementMapper;
  GCEImagePollingItemGenerator gceImagePollingItemGenerator = new GCEImagePollingItemGenerator(buildTriggerHelper);
  String gce_pipeline_artifact_snippet_runtime_all;
  String gce_pipeline_artifact_snippet_runtime_tagonly;
  String ngTriggerYaml_artifact_gce;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);

    ClassLoader classLoader = getClass().getClassLoader();
    gce_pipeline_artifact_snippet_runtime_all = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("gce_pipeline_artifact_snippet_runtime_all.yaml")),
        StandardCharsets.UTF_8);
    gce_pipeline_artifact_snippet_runtime_tagonly = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("gce_pipeline_artifact_snippet_runtime_tagonly.yaml")),
        StandardCharsets.UTF_8);

    ngTriggerYaml_artifact_gce = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-artifact-gce.yaml")), StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = ABHIPRANAV)
  @Category(UnitTests.class)
  public void testGCEImagePollingItemGeneration_pipelineContainsFixedValuesExceptTag() throws Exception {
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("acc", "org", "proj", null, ngTriggerYaml_artifact_gce, false);
    triggerDetails.getNgTriggerEntity().getMetadata().getBuildMetadata().setPollingConfig(
        PollingConfig.builder().signature("sig1").build());
    BuildTriggerOpsData buildTriggerOpsData = buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(
        triggerDetails, gce_pipeline_artifact_snippet_runtime_tagonly);
    PollingItem pollingItem = gceImagePollingItemGenerator.generatePollingItem(buildTriggerOpsData, null, false);

    PollingItemGeneratorTestHelper.baseAssert(pollingItem, io.harness.polling.contracts.Category.ARTIFACT);

    assertThat(pollingItem.getPollingPayloadData()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getConnectorRef()).isEqualTo("GCPConnector");
    assertThat(pollingItem.getPollingPayloadData().getGceImagePayload()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getGceImagePayload().getProject()).isEqualTo("cd-play");
  }

  @Test
  @Owner(developers = ABHIPRANAV)
  @Category(UnitTests.class)
  public void testGCEImagePollingItemGeneration_pipelineContainsAllRuntimeInputs() throws Exception {
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("acc", "org", "proj", null, ngTriggerYaml_artifact_gce, false);
    triggerDetails.getNgTriggerEntity().getMetadata().getBuildMetadata().setPollingConfig(
        PollingConfig.builder().signature("sig1").build());
    BuildTriggerOpsData buildTriggerOpsData = buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(
        triggerDetails, gce_pipeline_artifact_snippet_runtime_all);
    PollingItem pollingItem = gceImagePollingItemGenerator.generatePollingItem(buildTriggerOpsData, null, false);

    PollingItemGeneratorTestHelper.baseAssert(pollingItem, io.harness.polling.contracts.Category.ARTIFACT);

    assertThat(pollingItem.getPollingPayloadData()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getConnectorRef()).isEqualTo("GCPConnector");
    assertThat(pollingItem.getPollingPayloadData().getGceImagePayload()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getGceImagePayload().getProject()).isEqualTo("cd-play");
  }
}
