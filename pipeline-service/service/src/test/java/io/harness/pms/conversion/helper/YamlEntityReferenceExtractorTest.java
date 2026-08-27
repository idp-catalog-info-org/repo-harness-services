/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.helper;

import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.conversion.helper.YamlEntityReferenceExtractor.ExtractedReferences;
import io.harness.pms.conversion.helper.YamlEntityReferenceExtractor.PipelineChainReference;
import io.harness.pms.conversion.helper.YamlEntityReferenceExtractor.TemplateReference;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class YamlEntityReferenceExtractorTest extends CategoryTest {
  private static final String PIPELINE_WITH_TEMPLATE_REF = "pipeline:\n"
      + "  identifier: myPipeline\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1\n"
      + "        template:\n"
      + "          templateRef: myTemplate\n"
      + "          versionLabel: v1\n";

  private static final String PIPELINE_WITH_TEMPLATE_AND_GIT_BRANCH = "pipeline:\n"
      + "  identifier: myPipeline\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1\n"
      + "        template:\n"
      + "          templateRef: remoteTemplate\n"
      + "          versionLabel: v2\n"
      + "          gitBranch: feature-branch\n";

  private static final String PIPELINE_WITH_CHAINED_PIPELINE = "pipeline:\n"
      + "  identifier: parentPipeline\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: chainedStage\n"
      + "        type: Pipeline\n"
      + "        spec:\n"
      + "          org: default\n"
      + "          project: myProject\n"
      + "          pipeline: childPipeline\n";

  private static final String PIPELINE_WITH_MULTIPLE_REFS = "pipeline:\n"
      + "  identifier: complexPipeline\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1\n"
      + "        template:\n"
      + "          templateRef: template1\n"
      + "          versionLabel: v1\n"
      + "    - stage:\n"
      + "        identifier: s2\n"
      + "        template:\n"
      + "          templateRef: template2\n"
      + "          versionLabel: v2\n"
      + "    - stage:\n"
      + "        identifier: chainedStage\n"
      + "        type: Pipeline\n"
      + "        spec:\n"
      + "          pipeline: childPipeline\n";

  private static final String PIPELINE_WITH_DUPLICATE_TEMPLATE_REFS = "pipeline:\n"
      + "  identifier: dupPipeline\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1\n"
      + "        template:\n"
      + "          templateRef: sameTemplate\n"
      + "          versionLabel: v1\n"
      + "    - stage:\n"
      + "        identifier: s2\n"
      + "        template:\n"
      + "          templateRef: sameTemplate\n"
      + "          versionLabel: v1\n";

  private static final String PIPELINE_WITH_SCOPED_TEMPLATE_REFS = "pipeline:\n"
      + "  identifier: scopedPipeline\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1\n"
      + "        template:\n"
      + "          templateRef: account.accountTemplate\n"
      + "          versionLabel: v1\n"
      + "    - stage:\n"
      + "        identifier: s2\n"
      + "        template:\n"
      + "          templateRef: org.orgTemplate\n"
      + "          versionLabel: v1\n"
      + "    - stage:\n"
      + "        identifier: s3\n"
      + "        template:\n"
      + "          templateRef: projectTemplate\n"
      + "          versionLabel: v1\n";

  private static final String PIPELINE_WITH_NO_REFS = "pipeline:\n"
      + "  identifier: simplePipeline\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1\n"
      + "        type: Deployment\n"
      + "        spec:\n"
      + "          service:\n"
      + "            serviceRef: myService\n";

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExtractTemplateReference() {
    ExtractedReferences refs = YamlEntityReferenceExtractor.extractReferences(PIPELINE_WITH_TEMPLATE_REF);

    assertThat(refs.getTemplateReferences()).hasSize(1);
    TemplateReference ref = refs.getTemplateReferences().get(0);
    assertThat(ref.getTemplateRef()).isEqualTo("myTemplate");
    assertThat(ref.getVersionLabel()).isEqualTo("v1");
    assertThat(ref.getGitBranch()).isNull();
    assertThat(refs.getPipelineChainReferences()).isEmpty();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExtractTemplateReferenceWithGitBranch() {
    ExtractedReferences refs = YamlEntityReferenceExtractor.extractReferences(PIPELINE_WITH_TEMPLATE_AND_GIT_BRANCH);

    assertThat(refs.getTemplateReferences()).hasSize(1);
    TemplateReference ref = refs.getTemplateReferences().get(0);
    assertThat(ref.getTemplateRef()).isEqualTo("remoteTemplate");
    assertThat(ref.getVersionLabel()).isEqualTo("v2");
    assertThat(ref.getGitBranch()).isEqualTo("feature-branch");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExtractPipelineChainReference() {
    ExtractedReferences refs = YamlEntityReferenceExtractor.extractReferences(PIPELINE_WITH_CHAINED_PIPELINE);

    assertThat(refs.getTemplateReferences()).isEmpty();
    assertThat(refs.getPipelineChainReferences()).hasSize(1);
    PipelineChainReference ref = refs.getPipelineChainReferences().get(0);
    assertThat(ref.getPipelineIdentifier()).isEqualTo("childPipeline");
    assertThat(ref.getOrgIdentifier()).isEqualTo("default");
    assertThat(ref.getProjectIdentifier()).isEqualTo("myProject");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExtractMultipleReferences() {
    ExtractedReferences refs = YamlEntityReferenceExtractor.extractReferences(PIPELINE_WITH_MULTIPLE_REFS);

    assertThat(refs.getTemplateReferences()).hasSize(2);
    assertThat(refs.getTemplateReferences().get(0).getTemplateRef()).isEqualTo("template1");
    assertThat(refs.getTemplateReferences().get(1).getTemplateRef()).isEqualTo("template2");

    assertThat(refs.getPipelineChainReferences()).hasSize(1);
    assertThat(refs.getPipelineChainReferences().get(0).getPipelineIdentifier()).isEqualTo("childPipeline");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testDuplicateTemplateRefsAreDeduplicated() {
    ExtractedReferences refs = YamlEntityReferenceExtractor.extractReferences(PIPELINE_WITH_DUPLICATE_TEMPLATE_REFS);

    assertThat(refs.getTemplateReferences()).hasSize(1);
    assertThat(refs.getTemplateReferences().get(0).getTemplateRef()).isEqualTo("sameTemplate");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExtractScopedTemplateRefs() {
    ExtractedReferences refs = YamlEntityReferenceExtractor.extractReferences(PIPELINE_WITH_SCOPED_TEMPLATE_REFS);

    assertThat(refs.getTemplateReferences()).hasSize(3);
    assertThat(refs.getTemplateReferences().get(0).getTemplateRef()).isEqualTo("account.accountTemplate");
    assertThat(refs.getTemplateReferences().get(1).getTemplateRef()).isEqualTo("org.orgTemplate");
    assertThat(refs.getTemplateReferences().get(2).getTemplateRef()).isEqualTo("projectTemplate");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExtractNoReferences() {
    ExtractedReferences refs = YamlEntityReferenceExtractor.extractReferences(PIPELINE_WITH_NO_REFS);

    assertThat(refs.getTemplateReferences()).isEmpty();
    assertThat(refs.getPipelineChainReferences()).isEmpty();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExtractTemplateReferencesConvenience() {
    List<TemplateReference> refs = YamlEntityReferenceExtractor.extractTemplateReferences(PIPELINE_WITH_TEMPLATE_REF);

    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getTemplateRef()).isEqualTo("myTemplate");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExtractPipelineChainReferencesConvenience() {
    List<PipelineChainReference> refs =
        YamlEntityReferenceExtractor.extractPipelineChainReferences(PIPELINE_WITH_CHAINED_PIPELINE);

    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getPipelineIdentifier()).isEqualTo("childPipeline");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testChainedPipelineWithoutOrgAndProject() {
    String yaml = "pipeline:\n"
        + "  identifier: parentPipeline\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: chainedStage\n"
        + "        type: Pipeline\n"
        + "        spec:\n"
        + "          pipeline: childPipeline\n";

    ExtractedReferences refs = YamlEntityReferenceExtractor.extractReferences(yaml);

    assertThat(refs.getPipelineChainReferences()).hasSize(1);
    PipelineChainReference ref = refs.getPipelineChainReferences().get(0);
    assertThat(ref.getPipelineIdentifier()).isEqualTo("childPipeline");
    assertThat(ref.getOrgIdentifier()).isNull();
    assertThat(ref.getProjectIdentifier()).isNull();
  }
}
