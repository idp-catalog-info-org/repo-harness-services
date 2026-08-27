/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactSource;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.KubernetesServiceSpec.KubernetesServiceSpecBuilder;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Assembles the in-memory {@link NGServiceConfig} bean graph for a Kubernetes {@code service:}-rooted YAML and
 * serializes it to the YAML expected by {@code ServiceResourceV2}. The manifest node(s) and artifact source are built
 * by the per-source provisioners and passed in; this builder only assembles them into the service spec, so the emitted
 * YAML is byte-identical regardless of which provider produced them.
 */
@OwnedBy(HarnessTeam.CDC)
public final class OnboardingServiceYamlBuilder {
  private OnboardingServiceYamlBuilder() {}

  /**
   * Builds a fresh Kubernetes {@code service:}-rooted YAML from the provisioned manifest and artifact nodes.
   *
   * @param serviceId identifier of the service (must match the {@code ServiceRequestDTO})
   * @param serviceName name of the service
   * @param manifests the manifest node(s) to attach, or {@code null} when the request carries no manifest
   * @param artifactSource the artifact source to attach, or {@code null} when the request carries no artifact
   */
  public static String buildServiceYaml(OnboardingContextDTO context, String serviceId, String serviceName,
      List<ManifestConfigWrapper> manifests, ArtifactSource artifactSource) {
    NGServiceConfig ngServiceConfig =
        NGServiceConfig.builder()
            .ngServiceV2InfoConfig(
                NGServiceV2InfoConfig.builder()
                    .identifier(serviceId)
                    .name(serviceName)
                    .serviceDefinition(ServiceDefinition.builder()
                                           .type(ServiceDefinitionType.KUBERNETES)
                                           .serviceSpec(buildServiceSpec(context, manifests, artifactSource))
                                           .build())
                    .build())
            .build();

    try {
      return toPrunedYaml(ngServiceConfig);
    } catch (JsonProcessingException e) {
      throw new InvalidRequestException("Failed to serialize the onboarding service definition to YAML", e);
    }
  }

  /**
   * Merges the onboarding context into an <b>existing</b> service's YAML and re-serializes it. Only the sections
   * present in the request (manifest and/or artifact) are overlaid; every other part of the existing service
   * (name, description, tags, variables, other manifests, config files, hooks, ...) is preserved. Used when the
   * caller targets an already-created service, so a partial request does not wipe untouched sections.
   *
   * @param existingYaml the current {@code service:}-rooted YAML of the service being updated
   * @param manifests the manifest node(s) to overlay, or {@code null} when the request carries no manifest
   * @param artifactSource the artifact source to overlay, or {@code null} when the request carries no artifact
   */
  public static String mergeServiceYaml(String existingYaml, OnboardingContextDTO context,
      List<ManifestConfigWrapper> manifests, ArtifactSource artifactSource) {
    NGServiceConfig existing;
    try {
      existing = YamlPipelineUtils.read(existingYaml, NGServiceConfig.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Failed to parse the existing service YAML for update", e);
    }
    NGServiceV2InfoConfig info = existing.getNgServiceV2InfoConfig();
    if (info == null || info.getServiceDefinition() == null
        || !(info.getServiceDefinition().getServiceSpec() instanceof KubernetesServiceSpec)) {
      throw new InvalidRequestException(
          "Onboarding can only update a Kubernetes service; the existing service has a different definition");
    }

    KubernetesServiceSpec existingSpec = (KubernetesServiceSpec) info.getServiceDefinition().getServiceSpec();
    // Rebuild the spec (it is immutable) preserving all existing fields, overlaying only the requested sections.
    KubernetesServiceSpecBuilder specBuilder = KubernetesServiceSpec.builder()
                                                   .uuid(existingSpec.getUuid())
                                                   .variables(existingSpec.getVariables())
                                                   .artifacts(existingSpec.getArtifacts())
                                                   .manifests(existingSpec.getManifests())
                                                   .configFiles(existingSpec.getConfigFiles())
                                                   .appsetConfigs(existingSpec.getAppsetConfigs())
                                                   .hooks(existingSpec.getHooks())
                                                   .manifestConfigurations(existingSpec.getManifestConfigurations())
                                                   .release(existingSpec.getRelease())
                                                   .metadata(existingSpec.getMetadata());
    if (manifests != null) {
      specBuilder.manifests(mergeManifests(existingSpec.getManifests(), manifests));
    }
    if (artifactSource != null) {
      specBuilder.artifacts(buildArtifacts(context, artifactSource));
    }
    info.getServiceDefinition().setServiceSpec(specBuilder.build());

    try {
      return toPrunedYaml(existing);
    } catch (JsonProcessingException e) {
      throw new InvalidRequestException("Failed to serialize the updated onboarding service definition to YAML", e);
    }
  }

  /**
   * Builds the Kubernetes service spec, attaching the manifest node(s) (when present) and/or the artifact source
   * (when present).
   */
  private static KubernetesServiceSpec buildServiceSpec(
      OnboardingContextDTO context, List<ManifestConfigWrapper> manifests, ArtifactSource artifactSource) {
    KubernetesServiceSpecBuilder specBuilder = KubernetesServiceSpec.builder();
    if (manifests != null) {
      specBuilder.manifests(manifests);
    }
    if (artifactSource != null) {
      specBuilder.artifacts(buildArtifacts(context, artifactSource));
    }
    return specBuilder.build();
  }

  /**
   * Overlays the onboarding manifest(s) onto an existing service's manifest list. Any existing manifest whose
   * identifier matches one of the incoming manifests is replaced in place; every other existing manifest is
   * preserved. Honors the {@link #mergeServiceYaml} contract that untouched manifests survive an update.
   */
  private static List<ManifestConfigWrapper> mergeManifests(
      List<ManifestConfigWrapper> existing, List<ManifestConfigWrapper> incoming) {
    if (existing == null || existing.isEmpty()) {
      return incoming;
    }
    Set<String> incomingIds =
        incoming.stream().map(wrapper -> wrapper.getManifest().getIdentifier()).collect(Collectors.toSet());
    List<ManifestConfigWrapper> merged = new ArrayList<>();
    for (ManifestConfigWrapper wrapper : existing) {
      if (!incomingIds.contains(wrapper.getManifest().getIdentifier())) {
        merged.add(wrapper);
      }
    }
    merged.addAll(incoming);
    return merged;
  }

  /**
   * Wraps the provisioned artifact source into the primary artifact list. The primary artifact source we just
   * attached is pinned (rather than left as a runtime input).
   */
  private static ArtifactListConfig buildArtifacts(OnboardingContextDTO context, ArtifactSource artifactSource) {
    // Pin the primary artifact to the source we just attached, rather than leaving it a runtime input.
    PrimaryArtifact primaryArtifact = PrimaryArtifact.builder()
                                          .primaryArtifactRef(ParameterField.createValueField(context.getArtifactId()))
                                          .sources(Collections.singletonList(artifactSource))
                                          .build();

    return ArtifactListConfig.builder().primary(primaryArtifact).build();
  }

  /**
   * Serializes the bean graph to YAML, then drops every key whose value is an explicit {@code null}, so the persisted
   * service YAML is limited to the fields the onboarding request actually supplied. See
   * {@link OnboardingYamlUtils#toPrunedYaml(Object)} for why unset {@code ParameterField}s otherwise serialize as
   * {@code field: null}.
   */
  private static String toPrunedYaml(Object bean) throws JsonProcessingException {
    return OnboardingYamlUtils.toPrunedYaml(bean);
  }
}
