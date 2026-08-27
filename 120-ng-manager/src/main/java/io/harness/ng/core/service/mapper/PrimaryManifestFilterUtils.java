/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mapper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.pms.yaml.ParameterField;
import io.harness.unified.cd.service.manifests.ManifestConfig;
import io.harness.unified.cd.service.manifests.ManifestWrapper.ManifestWrapperBuilder;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Utility class for manifest filtering operations.

 */
@UtilityClass
public class PrimaryManifestFilterUtils {
  /**
   * When {@code primaryManifestRef} is a static identifier, removes every manifest source whose v0
   * {@link ManifestConfigType} matches the primary's type except the primary manifest itself. Other
   * manifest types are unchanged. Expression-based primary refs are left as-is (primary cannot be
   * resolved at conversion time).
   *
   * @param manifests the list of manifest config wrappers to filter
   * @param primaryManifestRef the primary manifest reference
   * @return filtered list of manifest config wrappers
   */
  public static List<ManifestConfigWrapper> filterManifestWrappersForPrimary(
      List<ManifestConfigWrapper> manifests, ParameterField<String> primaryManifestRef) {
    if (isEmpty(manifests) || primaryManifestRef == null || !ParameterField.isNotNull(primaryManifestRef)) {
      return manifests;
    }
    if (primaryManifestRef.isExpression()) {
      return manifests;
    }
    String primaryId = primaryManifestRef.obtainValue();
    if (primaryId == null || primaryId.isBlank()) {
      return manifests;
    }
    ManifestConfigWrapper primaryWrapper =
        manifests.stream().filter(w -> primaryId.equals(w.getManifest().getIdentifier())).findFirst().orElse(null);
    if (primaryWrapper == null) {
      return manifests;
    }
    ManifestConfigType primaryType = primaryWrapper.getManifest().getType();
    return manifests.stream()
        .filter(
            w -> !primaryType.equals(w.getManifest().getType()) || primaryId.equals(w.getManifest().getIdentifier()))
        .collect(Collectors.toList());
  }

  /**
   * Sets the primary manifest reference in the manifest wrapper builder.
   * Handles both expression-based and value-based primary manifest references.
   *
   * @param manifestWrapperBuilder the builder to set primary manifest ref on
   * @param primaryManifestRef the primary manifest reference
   */
  public static void setPrimaryManifestRef(
      ManifestWrapperBuilder manifestWrapperBuilder, ParameterField<String> primaryManifestRef) {
    if (primaryManifestRef.isExpression()) {
      // If it's an expression, create a new expression-based primary manifest
      manifestWrapperBuilder.primary(
          ParameterField.createExpressionField(true, primaryManifestRef.getExpressionValue(), null, true));
    } else {
      // If it has a value, create a primary manifest with just the ID
      manifestWrapperBuilder.primary(
          ParameterField.createValueField(ManifestConfig.builder().id(primaryManifestRef.obtainValue()).build()));
    }
  }
}
