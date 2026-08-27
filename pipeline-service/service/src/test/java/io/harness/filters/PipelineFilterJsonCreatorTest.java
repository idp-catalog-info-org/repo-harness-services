/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filters;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.plancreator.pipeline.PipelineInfoConfig;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.yaml.utils.JsonPipelineUtils;

import java.io.IOException;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@CodePulse(module = ProductModule.CDS, components = HarnessModuleComponent.CDS_PIPELINE, unitCoverageRequired = false)
public class PipelineFilterJsonCreatorTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @InjectMocks PipelineFilterJsonCreator pipelineFilterJsonCreator;

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetReferredEntities() throws IOException {
    String pipelineYaml = "pipeline:\n"
        + "  name: deploy\n"
        + "  identifier: deploy\n"
        + "  projectIdentifier: pro\n"
        + "  orgIdentifier: org\n"
        + "  tags: {}\n"
        + "  properties:\n"
        + "    ci:\n"
        + "      codebase:\n"
        + "        connectorRef: Git_Connector\n"
        + "        build: <+input>\n"
        + "        sparseCheckout: []\n"
        + "  variables:\n"
        + "    - name: Var1\n"
        + "      type: Secret\n"
        + "      description: \"\"\n"
        + "      required: false\n"
        + "      value: github_token\n";
    YamlField pipelineYamlField = YamlUtils.injectUuidInYamlField(pipelineYaml);
    FilterCreationContext context =
        FilterCreationContext.builder()
            .setupMetadata(SetupMetadata.newBuilder().setAccountId("acc").setOrgId("org").setProjectId("pro").build())
            .currentField(pipelineYamlField.getNode().getField("pipeline"))
            .build();

    List<EntityDetailProtoDTO> referredEntities = pipelineFilterJsonCreator.getReferredEntities(context,
        JsonPipelineUtils.read(pipelineYamlField.getNode().getField("pipeline").getNode().getCurrJsonNode().toString(),
            PipelineInfoConfig.class));
    assertThat(referredEntities.size()).isEqualTo(2);

    assertThat(referredEntities.get(0).getType()).isEqualTo(EntityTypeProtoEnum.SECRETS);
    assertThat(referredEntities.get(0).getIdentifierRef().getOrgIdentifier().getValue()).isEqualTo("org");
    assertThat(referredEntities.get(0).getIdentifierRef().getProjectIdentifier().getValue()).isEqualTo("pro");
    assertThat(referredEntities.get(0).getIdentifierRef().getIdentifier().getValue()).isEqualTo("github_token");

    assertThat(referredEntities.get(1).getType()).isEqualTo(EntityTypeProtoEnum.CONNECTORS);
    assertThat(referredEntities.get(1).getIdentifierRef().getOrgIdentifier().getValue()).isEqualTo("org");
    assertThat(referredEntities.get(1).getIdentifierRef().getProjectIdentifier().getValue()).isEqualTo("pro");
    assertThat(referredEntities.get(1).getIdentifierRef().getIdentifier().getValue()).isEqualTo("Git_Connector");
  }
}
