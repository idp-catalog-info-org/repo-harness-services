/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.filter;

import static io.harness.beans.FeatureName.CI_SECRET_EXPRESSION_REFERENCES;
import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stages.SecurityStageNode;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.integrationstage.k8s.K8InitializeTaskUtils;
import io.harness.ci.execution.utils.validation.ValidationUtilsImpl;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.walktree.visitor.inputset.SimpleVisitorFactory;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.STO)
@RunWith(MockitoJUnitRunner.class)
public class STOStageFilterJsonCreatorTest {
  @InjectMocks private STOStageFilterJsonCreator stoStageFilterJsonCreator;

  @Mock private ConnectorUtils connectorUtils;
  @Mock private SimpleVisitorFactory simpleVisitorFactory;
  @Mock private K8InitializeTaskUtils k8InitializeTaskUtils;
  @Mock private ValidationUtilsImpl validationUtils;
  @Mock private CIFeatureFlagService ciFeatureFlagService;

  private static final String ACCOUNT_ID = "kmpySmUISimoRrJL6NL73w";
  private static final String ORG_ID = "default";
  private static final String PROJECT_ID = "SahithiProject";

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetReferredEntitiesIncludesCodebaseSecretExpressions() throws IOException {
    when(ciFeatureFlagService.isEnabled(CI_SECRET_EXPRESSION_REFERENCES, ACCOUNT_ID)).thenReturn(true);

    assertThat(codebaseSecretFqns())
        .containsExactlyInAnyOrder(
            "pipeline.properties.ci.codebase.cloneDirectory", "pipeline.properties.ci.codebase.preFetchCommand");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetReferredEntitiesSkipsCodebaseSecretExpressionsWhenFeatureFlagIsOff() throws IOException {
    when(ciFeatureFlagService.isEnabled(CI_SECRET_EXPRESSION_REFERENCES, ACCOUNT_ID)).thenReturn(false);

    assertThat(codebaseSecretFqns()).isEmpty();
  }

  private Set<String> codebaseSecretFqns() throws IOException {
    final URL testFile = this.getClass().getClassLoader().getResource("pipeline_codebase_secret_expression_test.yml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    // The third stage of the fixture is the Security stage; it reads the pipeline level ci codebase.
    YamlField stageField = yamlField.getNode()
                               .getField("pipeline")
                               .getNode()
                               .getField("stages")
                               .getNode()
                               .asArray()
                               .get(2)
                               .getField("stage");

    FilterCreationContext filterCreationContext =
        FilterCreationContext.builder()
            .currentField(new YamlField("stage", stageField.getNode()))
            .setupMetadata(
                SetupMetadata.newBuilder().setAccountId(ACCOUNT_ID).setOrgId(ORG_ID).setProjectId(PROJECT_ID).build())
            .build();
    SecurityStageNode securityStageNode = YamlUtils.read(stageField.getNode().toString(), SecurityStageNode.class);

    return stoStageFilterJsonCreator.getReferredEntities(filterCreationContext, securityStageNode)
        .stream()
        .filter(reference -> reference.getType() == EntityTypeProtoEnum.SECRETS)
        .map(reference -> reference.getIdentifierRef().getMetadataMap().get("fqn"))
        .collect(Collectors.toSet());
  }
}
