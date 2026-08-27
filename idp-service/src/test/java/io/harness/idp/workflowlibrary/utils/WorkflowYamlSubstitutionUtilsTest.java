/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.workflowlibrary.utils;

import static io.harness.rule.OwnerRule.DIPENDRA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class WorkflowYamlSubstitutionUtilsTest extends CategoryTest {
  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSubstituteSymbolicRefs() {
    String yaml = "url: OOTB_PIPELINE_REF:scaffold-pipeline\n"
        + "other: OOTB_PIPELINE_REF:deploy-pipeline";
    Map<String, String> refToRealId = new HashMap<>();
    refToRealId.put("scaffold-pipeline",
        "https://app.harness.io/ng/account/abc/module/idp/orgs/org1/projects/proj1/pipelines/scaffold-pipeline/"
            + "pipeline-studio/?storeType=INLINE");
    refToRealId.put("deploy-pipeline",
        "https://app.harness.io/ng/account/abc/module/idp/orgs/org1/projects/proj1/pipelines/deploy-pipeline/"
            + "pipeline-studio/?storeType=INLINE");

    String result = WorkflowYamlSubstitutionUtils.substituteSymbolicRefs(yaml, refToRealId);

    assert result.contains("url: "
        + "https://app.harness.io/ng/account/abc/module/idp/orgs/org1/projects/proj1/pipelines/"
        + "scaffold-pipeline/pipeline-studio/?storeType=INLINE");
    assert result.contains("other: "
        + "https://app.harness.io/ng/account/abc/module/idp/orgs/org1/projects/proj1/pipelines/"
        + "deploy-pipeline/pipeline-studio/?storeType=INLINE");
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSubstituteSymbolicRefsPreservesUnmapped() {
    String yaml = "ref: OOTB_PIPELINE_REF:unknown-pipeline";
    Map<String, String> refToRealId = new HashMap<>();

    String result = WorkflowYamlSubstitutionUtils.substituteSymbolicRefs(yaml, refToRealId);

    assertEquals("ref: OOTB_PIPELINE_REF:unknown-pipeline", result);
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSubstituteSymbolicRefsNullSafety() {
    assertNull(WorkflowYamlSubstitutionUtils.substituteSymbolicRefs(null, new HashMap<>()));
    assertEquals("yaml", WorkflowYamlSubstitutionUtils.substituteSymbolicRefs("yaml", null));
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSubstituteAdminInputsSingleKey() {
    String yaml = "connectorRef: OOTB_ADMIN:git_connector_ref";
    Map<String, String> values = new HashMap<>();
    values.put("git_connector_ref", "account.myGithubConnector");

    String result = WorkflowYamlSubstitutionUtils.substituteAdminInputs(yaml, values);

    assertEquals("connectorRef: account.myGithubConnector", result);
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSubstituteAdminInputsMultipleOccurrences() {
    String yaml = "step1:\n  connectorRef: OOTB_ADMIN:git_connector_ref\n"
        + "step2:\n  connectorRef: OOTB_ADMIN:git_connector_ref\n"
        + "step3:\n  connectorRef: OOTB_ADMIN:git_connector_ref";
    Map<String, String> values = new HashMap<>();
    values.put("git_connector_ref", "org.sharedConn");

    String result = WorkflowYamlSubstitutionUtils.substituteAdminInputs(yaml, values);

    assertEquals("step1:\n  connectorRef: org.sharedConn\n"
            + "step2:\n  connectorRef: org.sharedConn\n"
            + "step3:\n  connectorRef: org.sharedConn",
        result);
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSubstituteAdminInputsMultipleKeys() {
    String yaml = "connectorRef: OOTB_ADMIN:git_connector_ref\ntoken: OOTB_ADMIN:slack_token";
    Map<String, String> values = new HashMap<>();
    values.put("git_connector_ref", "account.myConn");
    values.put("slack_token", "secret.slackBot");

    String result = WorkflowYamlSubstitutionUtils.substituteAdminInputs(yaml, values);

    assertEquals("connectorRef: account.myConn\ntoken: secret.slackBot", result);
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSubstituteAdminInputsNullSafety() {
    assertNull(WorkflowYamlSubstitutionUtils.substituteAdminInputs(null, new HashMap<>()));
    assertEquals("yaml", WorkflowYamlSubstitutionUtils.substituteAdminInputs("yaml", null));
    assertEquals("yaml", WorkflowYamlSubstitutionUtils.substituteAdminInputs("yaml", new HashMap<>()));
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSubstituteAdminInputsPreservesUnmatched() {
    String yaml = "ref: OOTB_ADMIN:unknown_key";
    Map<String, String> values = new HashMap<>();
    values.put("other_key", "value");

    String result = WorkflowYamlSubstitutionUtils.substituteAdminInputs(yaml, values);

    assertEquals("ref: OOTB_ADMIN:unknown_key", result);
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSubstituteAdminInputsInlineInCommand() {
    String yaml = "command: |\n  curl \"OOTB_ADMIN:harness_base_url/v1/scorecards/my-scorecard\"";
    Map<String, String> values = new HashMap<>();
    values.put("harness_base_url", "https://app.harness.io/gateway");

    String result = WorkflowYamlSubstitutionUtils.substituteAdminInputs(yaml, values);

    assertEquals("command: |\n  curl \"https://app.harness.io/gateway/v1/scorecards/my-scorecard\"", result);
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSubstituteScope() {
    String yaml = "orgIdentifier: {{orgIdentifier}}\nprojectIdentifier: {{projectIdentifier}}";

    String result = WorkflowYamlSubstitutionUtils.substituteScope(yaml, "my-org", "my-project");

    assertEquals("orgIdentifier: my-org\nprojectIdentifier: my-project", result);
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSubstituteScopeWithNulls() {
    String yaml = "org: {{orgIdentifier}} project: {{projectIdentifier}}";

    String result = WorkflowYamlSubstitutionUtils.substituteScope(yaml, null, null);

    assertEquals("org:  project: ", result);
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testSubstituteScopeNullYaml() {
    assertNull(WorkflowYamlSubstitutionUtils.substituteScope(null, "org", "project"));
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testInjectScopeFieldsProjectScope() {
    String yaml = "kind: Workflow\nidentifier: my_workflow\nname: My Workflow\nspec:\n  steps: []";
    String result = WorkflowYamlSubstitutionUtils.injectScopeFields(yaml, "my-org", "my-project");
    assert result.contains("orgIdentifier: my-org");
    assert result.contains("projectIdentifier: my-project");
    int idIdx = result.indexOf("identifier: my_workflow");
    int orgIdx = result.indexOf("orgIdentifier: my-org");
    assert orgIdx > idIdx;
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testInjectScopeFieldsOrgScope() {
    String yaml = "kind: Workflow\nidentifier: my_workflow\nname: My Workflow";
    String result = WorkflowYamlSubstitutionUtils.injectScopeFields(yaml, "my-org", null);
    assert result.contains("orgIdentifier: my-org");
    assert !result.contains("projectIdentifier");
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testInjectScopeFieldsAccountScope() {
    String yaml = "kind: Workflow\nidentifier: my_workflow\nname: My Workflow";
    String result = WorkflowYamlSubstitutionUtils.injectScopeFields(yaml, null, null);
    assert !result.contains("orgIdentifier");
    assert !result.contains("projectIdentifier");
    assertEquals(yaml + "\n", result);
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = DIPENDRA)
  public void testInjectScopeFieldsNullYaml() {
    assertNull(WorkflowYamlSubstitutionUtils.injectScopeFields(null, "org", "project"));
  }
}
