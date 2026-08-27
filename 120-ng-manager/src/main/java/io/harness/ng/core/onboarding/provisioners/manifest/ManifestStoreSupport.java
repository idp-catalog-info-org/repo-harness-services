/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.provisioners.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.pms.yaml.ParameterField;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * Store-assembly helpers shared by the manifest provisioners: path splitting, git fetch-ref selection and wrapping a
 * store into the {@code K8sManifest} node emitted into the service YAML. Extracted verbatim from the former
 * {@code OnboardingServiceYamlBuilder} so the manifest node is byte-identical to what the switch used to produce.
 */
@OwnedBy(HarnessTeam.CDC)
public final class ManifestStoreSupport {
  private ManifestStoreSupport() {}

  /**
   * Wraps a provider-specific store into the single {@code K8sManifest} node the onboarding service spec carries,
   * keyed by {@code manifest_id}.
   */
  public static ManifestConfigWrapper k8sManifest(OnboardingContextDTO context, StoreConfigWrapper store) {
    K8sManifest k8sManifest =
        K8sManifest.builder().identifier(context.getManifestId()).store(ParameterField.createValueField(store)).build();

    return ManifestConfigWrapper.builder()
        .manifest(ManifestConfig.builder()
                      .identifier(context.getManifestId())
                      .type(ManifestConfigType.K8_MANIFEST)
                      .spec(k8sManifest)
                      .build())
        .build();
  }

  /**
   * Splits the comma-separated file/folder paths, trimming each and dropping blanks, so {@code "a.yaml, b.yaml"} and
   * {@code "a.yaml,,b.yaml,"} both become {@code [a.yaml, b.yaml]}.
   */
  public static List<String> resolveManifestPaths(String manifestPaths) {
    if (StringUtils.isBlank(manifestPaths)) {
      return Collections.emptyList();
    }
    return Arrays.stream(manifestPaths.split(","))
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toList());
  }

  /**
   * Sets either commitId (when fetch type is COMMIT) or branch on the store builder. The caller has already validated
   * the matching ref is present, so neither is silently substituted for the other.
   */
  public static void applyGitFetchRef(OnboardingContextDTO context, FetchType gitFetchType,
      Consumer<ParameterField<String>> branchSetter, Consumer<ParameterField<String>> commitIdSetter) {
    if (gitFetchType == FetchType.COMMIT) {
      commitIdSetter.accept(ParameterField.createValueField(context.getManifestCommitId()));
    } else {
      branchSetter.accept(ParameterField.createValueField(context.getManifestBranch()));
    }
  }
}
