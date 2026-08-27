/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mapper;

import static io.harness.rule.OwnerRule.DANIEL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cdng.elastigroup.config.yaml.StartupScriptConfiguration;
import io.harness.cdng.manifest.yaml.HarnessCodeStore;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.unified.cd.service.startupscript.StartupScriptCodeStoreConfig;
import io.harness.unified.cd.service.startupscript.StartupScriptStoreType;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TemplateBasedStartupScriptMapperTest {
  private final TemplateBasedStartupScriptMapper mapper = new TemplateBasedStartupScriptMapper();

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testInlineStartupScriptReturnsNull() {
    StartupScriptConfiguration ngStartupScript =
        StartupScriptConfiguration.builder()
            .store(StoreConfigWrapper.builder()
                       .type(StoreConfigType.INLINE)
                       .spec(InlineStoreConfig.builder()
                                 .content(ParameterField.createValueField("#!/bin/bash\necho hi"))
                                 .build())
                       .build())
            .build();

    var unified = mapper.toUnifiedStartupScriptWithInputs(ngStartupScript);

    assertThat(unified).isNull();
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testHarnessCodeStartupScriptConversion() {
    StartupScriptConfiguration ngStartupScript =
        StartupScriptConfiguration.builder()
            .store(StoreConfigWrapper.builder()
                       .type(StoreConfigType.HARNESS_CODE)
                       .spec(HarnessCodeStore.builder()
                                 .gitFetchType(FetchType.BRANCH)
                                 .branch(ParameterField.createValueField("main"))
                                 .repoName(ParameterField.createValueField("my-repo"))
                                 .paths(ParameterField.createValueField(List.of("scripts/startup.sh")))
                                 .build())
                       .build())
            .build();

    var unified = mapper.toUnifiedStartupScriptWithInputs(ngStartupScript);

    assertThat(unified).isNotNull();
    assertThat(unified.getStore().getUses()).isEqualTo(StartupScriptStoreType.CODE);
    assertThat(unified.getStore().getWith()).isInstanceOf(StartupScriptCodeStoreConfig.class);
    StartupScriptCodeStoreConfig codeStore = (StartupScriptCodeStoreConfig) unified.getStore().getWith();
    assertThat(codeStore.getRepo().obtainValue()).isEqualTo("my-repo");
    assertThat(codeStore.getPaths().obtainValue()).containsExactly("scripts/startup.sh");
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testHarnessFileStoreStartupScriptReturnsNull() {
    StartupScriptConfiguration ngStartupScript =
        StartupScriptConfiguration.builder()
            .store(StoreConfigWrapper.builder()
                       .type(StoreConfigType.HARNESS)
                       .spec(io.harness.cdng.manifest.yaml.harness.HarnessStore.builder()
                                 .files(ParameterField.createValueField(List.of("/startup.sh")))
                                 .build())
                       .build())
            .build();

    assertThat(mapper.toUnifiedStartupScriptWithInputs(ngStartupScript)).isNull();
  }
}
