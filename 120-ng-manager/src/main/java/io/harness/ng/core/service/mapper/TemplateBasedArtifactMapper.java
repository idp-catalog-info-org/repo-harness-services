/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mapper;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.service.inputsmapper.ArtifactsInputsConstants.SIDECAR_ARTIFACT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactSource;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifactWrapper;
import io.harness.ng.core.service.registries.UnifiedConversionRegistry;
import io.harness.ng.core.service.registries.UnifiedConversionRegistry.ConversionResult;
import io.harness.pms.yaml.ParameterField;
import io.harness.unified.cd.service.artifacts.ArtifactConfig;
import io.harness.unified.cd.service.artifacts.ArtifactType;
import io.harness.unified.cd.service.artifacts.ArtifactWrapper;
import io.harness.unified.cd.service.artifacts.ArtifactWrapper.ArtifactWrapperBuilder;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OPTIMIZED V2: Template-based Artifact Mapper.
 * Uses UnifiedConversionRegistry for simplified, minimal-change onboarding.
 *
 * <p><strong>Changes from V1:</strong>
 * <ul>
 *   <li>Single registry dependency instead of two
 *   <li>One method call for both type conversion and template name
 *   <li>Cleaner code, easier to understand
 * </ul>
 */
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class TemplateBasedArtifactMapper {
  private final UnifiedConversionRegistry conversionRegistry;

  public ArtifactWrapper toUnifiedArtifactWrapperWithInputs(ArtifactListConfig artifacts) {
    if (artifacts == null) {
      return null;
    }

    ArtifactWrapperBuilder artifactWrapperBuilder = ArtifactWrapper.builder();
    List<ArtifactConfig> artifactSources = new ArrayList<>();

    // Handle primary artifacts
    handlePrimaryArtifacts(artifacts, artifactWrapperBuilder, artifactSources);

    // Handle sidecar artifacts
    List<SidecarArtifactWrapper> sidecars = artifacts.getSidecars();
    if (isNotEmpty(sidecars)) {
      sidecars.forEach(sidecar -> {
        ArtifactConfig sidecarConfig = toArtifactConfigWithInputs(sidecar.getSidecar().getSpec(), true);
        if (sidecarConfig != null) {
          artifactSources.add(sidecarConfig);
        }
      });
    }

    return artifactWrapperBuilder.sources(artifactSources).build();
  }

  /**
   * Handle primary artifacts for template path.
   */
  private void handlePrimaryArtifacts(ArtifactListConfig artifacts, ArtifactWrapperBuilder artifactWrapperBuilder,
      List<ArtifactConfig> artifactSources) {
    if (artifacts.getPrimary() == null) {
      return;
    }

    if (artifacts.getPrimary().getSpec() != null) {
      // Primary artifact specified directly
      ArtifactConfig primaryConfig = toArtifactConfigWithInputs(artifacts.getPrimary().getSpec(), false);
      if (primaryConfig != null) {
        artifactSources.add(primaryConfig);
        artifactWrapperBuilder.primary(ParameterField.createValueField(primaryConfig));
      }
      return;
    }

    // Primary artifact reference
    if (artifacts.getPrimary().getPrimaryArtifactRef() == null) {
      return;
    }

    if (artifacts.getPrimary().getPrimaryArtifactRef().isExpression()) {
      // Expression-based primary reference
      artifactWrapperBuilder.primary(ParameterField.createExpressionField(
          true, artifacts.getPrimary().getPrimaryArtifactRef().getExpressionValue(), null, false));
      // Add all sources
      List<ArtifactSource> primarySources = artifacts.getPrimary().getSources();
      if (isNotEmpty(primarySources)) {
        primarySources.forEach(source -> {
          ArtifactConfig config = toArtifactConfigWithInputs(source.getSpec(), false);
          if (config != null) {
            artifactSources.add(config);
          }
        });
      }
    } else {
      // Value-based primary reference - find matching source
      if (isNotEmpty(artifacts.getPrimary().getSources())) {
        String primaryRef = artifacts.getPrimary().getPrimaryArtifactRef().getValue();
        Optional<ArtifactSource> primarySourceOp = artifacts.getPrimary()
                                                       .getSources()
                                                       .stream()
                                                       .filter(source -> source.getIdentifier().equals(primaryRef))
                                                       .findFirst();

        if (primarySourceOp.isPresent()) {
          ArtifactConfig primaryConfig = toArtifactConfigWithInputs(primarySourceOp.get().getSpec(), false);
          if (primaryConfig != null) {
            artifactSources.add(primaryConfig);
            artifactWrapperBuilder.primary(ParameterField.createValueField(primaryConfig));
          }
        }
      }
    }
  }

  /**
   * Convert artifact config to unified artifact config with inputs mapping.
   */
  private ArtifactConfig toArtifactConfigWithInputs(
      io.harness.cdng.artifact.bean.ArtifactConfig artifactConfig, boolean sidecar) {
    // Single conversion call gets both unified type and template action
    ConversionResult<ArtifactType> result = conversionRegistry.convertArtifact(artifactConfig.getSourceType());

    if (result == null) {
      log.warn("Artifact type {} not onboarded for template-based conversion",
          artifactConfig.getSourceType().getDisplayName());
      return null;
    }

    Map<String, Object> inputsMap = new HashMap<>();
    inputsMap.put(SIDECAR_ARTIFACT, sidecar);

    return ArtifactConfig.builder()
        .id(artifactConfig.getIdentifier())
        .sidecar(sidecar)
        .uses(result.getUnifiedType())
        .action(result.getTemplateAction())
        .inputs(inputsMap)
        .build();
  }
}
