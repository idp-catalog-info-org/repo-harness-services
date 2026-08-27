/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.rule.OwnerRule.DANIEL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cd.beans.outcomes.ServiceConfigOutcome;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.unified.cd.service.configfiles.ConfigFile;
import io.harness.unified.cd.service.manifests.FetchType;
import io.harness.unified.cd.service.spec.SpotServiceSpec;
import io.harness.unified.cd.service.startupscript.StartupScriptCodeStoreConfig;
import io.harness.unified.cd.service.startupscript.StartupScriptConfiguration;
import io.harness.unified.cd.service.startupscript.StartupScriptStoreType;
import io.harness.unified.cd.service.startupscript.StartupScriptStoreWrapper;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SpotStartupScriptHelperTest {
  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void buildInitialOutcome_codeIncludesFetchMetadata() {
    StartupScriptConfiguration startupScript =
        StartupScriptConfiguration.builder()
            .store(StartupScriptStoreWrapper.builder()
                       .uses(StartupScriptStoreType.CODE)
                       .with(StartupScriptCodeStoreConfig.builder()
                                 .connector(ParameterField.createValueField("account.code"))
                                 .type(FetchType.BRANCH)
                                 .branch(ParameterField.createValueField("main"))
                                 .repo(ParameterField.createValueField("my-repo"))
                                 .paths(ParameterField.createValueField(List.of("scripts/startup.sh")))
                                 .build())
                       .build())
            .build();

    Map<String, Object> outcome = SpotStartupScriptHelper.buildInitialOutcome(startupScript);

    assertThat(outcome).containsEntry("action", SpotStartupScriptHelper.STARTUP_SCRIPT_CODE_ACTION);
    assertThat(outcome).containsEntry("repoName", "my-repo");
    assertThat(outcome).containsEntry(SpotStartupScriptHelper.PATHS_KEY, List.of("scripts/startup.sh"));
    assertThat(outcome).doesNotContainKey("content");
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void validateStoreType_allowsCodeOnly() {
    // CODE is the only supported store type — no exception expected
    SpotStartupScriptHelper.validateStoreType(
        StartupScriptStoreWrapper.builder().uses(StartupScriptStoreType.CODE).build());
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void requiresCodeFetch_trueForCodeStore() {
    StartupScriptConfiguration codeScript =
        StartupScriptConfiguration.builder()
            .store(StartupScriptStoreWrapper.builder().uses(StartupScriptStoreType.CODE).build())
            .build();

    assertThat(SpotStartupScriptHelper.requiresCodeFetch(codeScript)).isTrue();
    assertThat(SpotStartupScriptHelper.requiresCodeFetch(null)).isFalse();
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void buildConfigFile_usesStartupScriptFetchInputs() {
    StartupScriptConfiguration startupScript =
        StartupScriptConfiguration.builder()
            .store(StartupScriptStoreWrapper.builder()
                       .uses(StartupScriptStoreType.CODE)
                       .with(StartupScriptCodeStoreConfig.builder()
                                 .connector(ParameterField.createValueField("account.code"))
                                 .type(FetchType.BRANCH)
                                 .branch(ParameterField.createValueField("main"))
                                 .repo(ParameterField.createValueField("my-repo"))
                                 .paths(ParameterField.createValueField(List.of("scripts/startup.sh")))
                                 .build())
                       .build())
            .build();

    ConfigFile configFile = SpotStartupScriptHelper.buildConfigFile(startupScript);

    assertThat(configFile.getId()).isEqualTo(SpotStartupScriptHelper.STARTUP_SCRIPT_UNIT_ID);
    assertThat(configFile.getInputs())
        .containsEntry(SpotStartupScriptHelper.STORE_TYPE_KEY, StartupScriptStoreType.CODE.getDisplayName())
        .containsEntry("repoName", "my-repo")
        .containsEntry(SpotStartupScriptHelper.PATHS_KEY, List.of("scripts/startup.sh"));
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void patchStartupScriptPaths_setsPathsOnOutcome() {
    ServiceConfigOutcome original =
        ServiceConfigOutcome.builder().startupScript(Map.of(SpotStartupScriptHelper.STORE_TYPE_KEY, "code")).build();

    ServiceConfigOutcome patched =
        SpotStartupScriptHelper.patchStartupScriptPaths(original, List.of("/workspace/startupScript/startup.sh"));

    assertThat(patched.getStartupScript())
        .containsEntry(SpotStartupScriptHelper.PATHS_KEY, "/workspace/startupScript/startup.sh");
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void patchStartupScriptPaths_multiplePathsJoinedWithComma() {
    ServiceConfigOutcome original = ServiceConfigOutcome.builder().startupScript(Map.of()).build();

    ServiceConfigOutcome patched = SpotStartupScriptHelper.patchStartupScriptPaths(
        original, List.of("/workspace/startupScript/a.sh", "/workspace/startupScript/b.sh"));

    assertThat(patched.getStartupScript())
        .containsEntry(
            SpotStartupScriptHelper.PATHS_KEY, "/workspace/startupScript/a.sh,/workspace/startupScript/b.sh");
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void hasStartupScript_trueForSpotServiceWithStore() {
    SpotServiceSpec spec =
        SpotServiceSpec.builder()
            .startupScript(
                StartupScriptConfiguration.builder()
                    .store(StartupScriptStoreWrapper.builder()
                               .uses(StartupScriptStoreType.CODE)
                               .with(StartupScriptCodeStoreConfig.builder()
                                         .repo(ParameterField.createValueField("my-repo"))
                                         .paths(ParameterField.createValueField(List.of("scripts/startup.sh")))
                                         .build())
                               .build())
                    .build())
            .build();

    assertThat(SpotServiceSpec.hasStartupScript(spec)).isTrue();
  }
}
