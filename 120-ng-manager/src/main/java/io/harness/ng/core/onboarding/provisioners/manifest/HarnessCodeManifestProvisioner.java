/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.provisioners.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.HarnessCodeStore;
import io.harness.cdng.manifest.yaml.HarnessCodeStore.HarnessCodeStoreBuilder;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.ng.core.onboarding.mapper.ManifestProviderType;
import io.harness.ng.core.onboarding.mapper.OnboardingContextNormalizer;
import io.harness.ng.core.onboarding.provisioners.spec.ManifestProvisioner;
import io.harness.ng.core.onboarding.support.OnboardingProvisionContext;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

/**
 * Harness Code manifest source: the built-in Git provider. The connection is implicit, so onboarding creates neither
 * a connector nor a secret — only the store is emitted. Harness Code needs a repo name to identify the built-in repo
 * the manifest is fetched from.
 */
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class HarnessCodeManifestProvisioner implements ManifestProvisioner {
  @Override
  public ManifestProviderType type() {
    return ManifestProviderType.HARNESS_CODE;
  }

  @Override
  public boolean requiresConnector() {
    return ManifestProviderType.HARNESS_CODE.requiresConnector();
  }

  @Override
  public void validate(OnboardingContextDTO context) {
    if (StringUtils.isBlank(context.getManifestRepoName())) {
      throw new InvalidRequestException("manifest_repoName is required for a harnessCode manifest");
    }
  }

  @Override
  public ConnectorInfoDTO buildConnector(OnboardingProvisionContext provisionContext) {
    // Harness Code's connection is built-in; no connector is provisioned.
    return null;
  }

  @Override
  public ManifestConfigWrapper buildManifest(OnboardingContextDTO context, String connectorRef) {
    // Harness Code has no connectorRef; the connection is built-in. repoName identifies the Harness Code repo.
    FetchType gitFetchType = OnboardingContextNormalizer.resolveGitFetchType(context.getManifestFetchType());
    HarnessCodeStoreBuilder storeBuilder =
        HarnessCodeStore.builder()
            .gitFetchType(gitFetchType)
            .paths(
                ParameterField.createValueField(ManifestStoreSupport.resolveManifestPaths(context.getManifestPaths())));
    if (StringUtils.isNotBlank(context.getManifestRepoName())) {
      storeBuilder.repoName(ParameterField.createValueField(context.getManifestRepoName()));
    }
    ManifestStoreSupport.applyGitFetchRef(context, gitFetchType, storeBuilder::branch, storeBuilder::commitId);
    StoreConfigWrapper store =
        StoreConfigWrapper.builder().type(StoreConfigType.HARNESS_CODE).spec(storeBuilder.build()).build();
    return ManifestStoreSupport.k8sManifest(context, store);
  }
}
