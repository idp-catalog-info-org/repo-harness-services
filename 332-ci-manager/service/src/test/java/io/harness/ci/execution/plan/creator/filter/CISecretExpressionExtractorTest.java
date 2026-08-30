/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.filter;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CI)
public class CISecretExpressionExtractorTest {
  private static final String ACCOUNT_ID = "kmpySmUISimoRrJL6NL73w";
  private static final String ORG_ID = "default";
  private static final String PROJECT_ID = "SahithiProject";
  private static final String FQN = "fqn";

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractFromCodebase() throws IOException {
    FilterCreationContext filterCreationContext = contextForStage("pipeline_codebase_secret_expression_test.yml", 0);

    List<EntityDetailProtoDTO> references = CISecretExpressionExtractor.extractFromCodebase(filterCreationContext);

    assertThat(references).hasSize(2);
    assertThat(references).allMatch(reference -> reference.getType() == EntityTypeProtoEnum.SECRETS);
    assertThat(fqnByIdentifier(references))
        .containsEntry("cloneDirSecret", "pipeline.properties.ci.codebase.cloneDirectory")
        .containsEntry("preFetchSecret", "pipeline.properties.ci.codebase.preFetchCommand");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractFromCodebaseFqnIsPipelineLevelRegardlessOfVisitedStage() throws IOException {
    FilterCreationContext filterCreationContext = contextForStage("pipeline_codebase_secret_expression_test.yml", 1);

    List<EntityDetailProtoDTO> references = CISecretExpressionExtractor.extractFromCodebase(filterCreationContext);

    assertThat(fqnByIdentifier(references))
        .containsEntry("cloneDirSecret", "pipeline.properties.ci.codebase.cloneDirectory")
        .containsEntry("preFetchSecret", "pipeline.properties.ci.codebase.preFetchCommand");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractFromCodebaseWithoutCodebaseReturnsEmpty() throws IOException {
    FilterCreationContext filterCreationContext = contextForStage("pipeline_secret_variable_test.yml", 0);

    assertThat(CISecretExpressionExtractor.extractFromCodebase(filterCreationContext)).isEmpty();
  }

  private Map<String, String> fqnByIdentifier(List<EntityDetailProtoDTO> references) {
    return references.stream().collect(Collectors.toMap(reference
        -> reference.getIdentifierRef().getIdentifier().getValue(),
        reference -> reference.getIdentifierRef().getMetadataMap().get(FQN)));
  }

  private FilterCreationContext contextForStage(String resourceName, int stageIndex) throws IOException {
    final URL testFile = this.getClass().getClassLoader().getResource(resourceName);
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    YamlNode stageNode = yamlField.getNode()
                             .getField("pipeline")
                             .getNode()
                             .getField("stages")
                             .getNode()
                             .asArray()
                             .get(stageIndex)
                             .getField("stage")
                             .getNode();
    return FilterCreationContext.builder()
        .currentField(new YamlField("stage", stageNode))
        .setupMetadata(
            SetupMetadata.newBuilder().setAccountId(ACCOUNT_ID).setOrgId(ORG_ID).setProjectId(PROJECT_ID).build())
        .build();
  }
}
