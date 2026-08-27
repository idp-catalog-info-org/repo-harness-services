/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.elastigroup.config.yaml.StartupScriptConfiguration;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.HarnessCodeStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.unified.cd.service.startupscript.StartupScriptCodeStoreConfig;
import io.harness.unified.cd.service.startupscript.StartupScriptStoreType;
import io.harness.unified.cd.service.startupscript.StartupScriptStoreWrapper;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps NG Elastigroup {@code startupScript} to unified {@code startup-script} for Spot services.
 */
@Slf4j
@OwnedBy(HarnessTeam.CI)
@Singleton
public class TemplateBasedStartupScriptMapper {
  public io.harness.unified.cd.service.startupscript.StartupScriptConfiguration toUnifiedStartupScriptWithInputs(
      StartupScriptConfiguration startupScriptNG) {
    if (startupScriptNG == null || startupScriptNG.getStore() == null) {
      return null;
    }

    StoreConfigWrapper storeWrapper = startupScriptNG.getStore();
    if (storeWrapper == null || storeWrapper.getSpec() == null) {
      return null;
    }

    StoreConfig storeConfig = storeWrapper.getSpec();
    StartupScriptStoreWrapper unifiedStore = mapStore(storeConfig);
    if (unifiedStore == null) {
      return null;
    }

    return io.harness.unified.cd.service.startupscript.StartupScriptConfiguration.builder().store(unifiedStore).build();
  }

  private StartupScriptStoreWrapper mapStore(StoreConfig storeConfig) {
    String storeKind = storeConfig.getKind();
    if (ManifestStoreType.HARNESS_CODE.equals(storeKind) && storeConfig instanceof HarnessCodeStore harnessCodeStore) {
      StartupScriptCodeStoreConfig codeStore = StartupScriptCodeStoreConfig.builder()
                                                   .connector(harnessCodeStore.getConnectorRef())
                                                   .type(toUnifiedFetchType(harnessCodeStore.getGitFetchType()))
                                                   .branch(harnessCodeStore.getBranch())
                                                   .commitId(harnessCodeStore.getCommitId())
                                                   .repo(harnessCodeStore.getRepoName())
                                                   .paths(harnessCodeStore.getPaths())
                                                   .folderPath(harnessCodeStore.getFolderPath())
                                                   .build();
      return StartupScriptStoreWrapper.builder().uses(StartupScriptStoreType.CODE).with(codeStore).build();
    }

    log.debug("Startup script store type [{}] is not supported for unified Spot conversion", storeKind);
    return null;
  }

  private static io.harness.unified.cd.service.manifests.FetchType toUnifiedFetchType(FetchType ngFetchType) {
    if (ngFetchType == null) {
      return io.harness.unified.cd.service.manifests.FetchType.BRANCH;
    }
    return switch (ngFetchType) {
      case BRANCH -> io.harness.unified.cd.service.manifests.FetchType.BRANCH;
      case COMMIT -> io.harness.unified.cd.service.manifests.FetchType.COMMIT;
      default -> io.harness.unified.cd.service.manifests.FetchType.BRANCH;
    };
  }
}
