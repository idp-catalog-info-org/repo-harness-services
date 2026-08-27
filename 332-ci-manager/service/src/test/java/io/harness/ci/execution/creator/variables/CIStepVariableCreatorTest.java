/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.creator.variables;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.SATYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CI)
public class CIStepVariableCreatorTest extends CategoryTest {
  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    CIStepVariableCreator ciStepVariableCreator = new CIStepVariableCreator();
    Set<String> supportedStepTypes = ciStepVariableCreator.getSupportedStepTypes();

    // Verify that the supported step types include SaveCache and RestoreCache
    /*    assertThat(supportedStepTypes).contains("SaveCache");
        assertThat(supportedStepTypes).contains("RestoreCache");*/

    // Verify other important step types are included
    assertThat(supportedStepTypes).contains("SaveCacheS3");
    assertThat(supportedStepTypes).contains("RestoreCacheS3");
    assertThat(supportedStepTypes).contains("RestoreCacheGCS");
    assertThat(supportedStepTypes).contains("SaveCacheGCS");

    // Verify CI specific step types
    assertThat(supportedStepTypes).contains("run");
    assertThat(supportedStepTypes).contains("plugin");
    assertThat(supportedStepTypes).contains("test");
    assertThat(supportedStepTypes).contains("background");

    // Verify build and push step types
    assertThat(supportedStepTypes).contains("BuildAndPushGCR");
    assertThat(supportedStepTypes).contains("BuildAndPushACR");
    assertThat(supportedStepTypes).contains("BuildAndPushECR");
    assertThat(supportedStepTypes).contains("BuildAndPushDockerRegistry");

    // Verify upload step types
    assertThat(supportedStepTypes).contains("S3Upload");
    assertThat(supportedStepTypes).contains("GCSUpload");
    assertThat(supportedStepTypes).contains("ArtifactoryUpload");

    // Verify other step types
    assertThat(supportedStepTypes).contains("GitClone");
    assertThat(supportedStepTypes).contains("Security");
    assertThat(supportedStepTypes).contains("Plugin");
    assertThat(supportedStepTypes).contains("Action");
    assertThat(supportedStepTypes).contains("Bitrise");
    assertThat(supportedStepTypes).contains("AiVerify");

    // Test case variations
    assertThat(supportedStepTypes).contains("Test");
    assertThat(supportedStepTypes).contains("clone");
    assertThat(supportedStepTypes).contains("run-test");
    assertThat(supportedStepTypes).contains("action");
    assertThat(supportedStepTypes).contains("bitrise");

    assertThat(supportedStepTypes).contains("AiTestAutomation");

    assertThat(supportedStepTypes).hasSize(31);
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testCacheStepTypesIncluded() {
    CIStepVariableCreator ciStepVariableCreator = new CIStepVariableCreator();
    Set<String> supportedStepTypes = ciStepVariableCreator.getSupportedStepTypes();

    // Specifically test that all cache-related step types are supported
    assertThat(supportedStepTypes)
        .containsAll(
            Set.of("SaveCache", "RestoreCache", "SaveCacheS3", "RestoreCacheS3", "SaveCacheGCS", "RestoreCacheGCS"));
  }
}
