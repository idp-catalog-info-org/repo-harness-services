/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.mapper;

import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.yaml.ArtifactSource;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO.OnboardingContextDTOBuilder;
import io.harness.ng.core.onboarding.provisioners.artifact.DockerArtifactProvisioner;
import io.harness.ng.core.onboarding.provisioners.manifest.GithubManifestProvisioner;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.rule.Owner;
import io.harness.utils.YamlPipelineUtils;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Unit tests for {@link OnboardingServiceYamlBuilder}. The builder no longer builds the manifest/artifact nodes itself
 * (that moved to the per-source provisioners); its remaining job is to assemble already-built nodes into a Kubernetes
 * {@code service:}-rooted YAML, merge them into an existing service by identifier, and prune unset fields. The tests
 * build real nodes via the GitHub/Docker provisioners (the production path) and assert on the produced YAML parsed back
 * into the {@link NGServiceConfig} bean graph.
 */
public class OnboardingServiceYamlBuilderTest extends CategoryTest {
  private static final String MANIFEST_CONNECTOR_REF = "manifest_connector";
  private static final String ARTIFACT_CONNECTOR_REF = "artifact_connector";

  // buildManifest/buildArtifactSource never touch the injected secret-creation dependency (only buildConnector does),
  // so a null is safe for exercising node building here.
  private static final GithubManifestProvisioner MANIFEST_PROVISIONER = new GithubManifestProvisioner(null);
  private static final DockerArtifactProvisioner ARTIFACT_PROVISIONER = new DockerArtifactProvisioner(null);

  private static OnboardingContextDTOBuilder githubManifestContext(String manifestId) {
    return OnboardingContextDTO.builder()
        .manifestId(manifestId)
        .manifestBranch("main")
        .manifestPaths("k8s/deployment.yaml");
  }

  private static List<ManifestConfigWrapper> manifestNodes(OnboardingContextDTO context, String connectorRef) {
    return List.of(MANIFEST_PROVISIONER.buildManifest(context, connectorRef));
  }

  private static ArtifactSource artifactNode(OnboardingContextDTO context, String connectorRef) {
    return ARTIFACT_PROVISIONER.buildArtifactSource(context, connectorRef);
  }

  private static KubernetesServiceSpec parseSpec(String yaml) throws Exception {
    NGServiceConfig config = YamlPipelineUtils.read(yaml, NGServiceConfig.class);
    return (KubernetesServiceSpec) config.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec();
  }

  private static List<String> manifestIds(KubernetesServiceSpec spec) {
    return spec.getManifests()
        .stream()
        .map(wrapper -> wrapper.getManifest().getIdentifier())
        .collect(java.util.stream.Collectors.toList());
  }

  // ---- buildServiceYaml (create) ----

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testBuildServiceYamlAttachesManifestAndArtifact() throws Exception {
    OnboardingContextDTO context =
        githubManifestContext("app_manifest").artifactId("app_artifact").artifactImagePath("library/nginx").build();

    String yaml = OnboardingServiceYamlBuilder.buildServiceYaml(context, "svc_id", "svc name",
        manifestNodes(context, MANIFEST_CONNECTOR_REF), artifactNode(context, ARTIFACT_CONNECTOR_REF));

    KubernetesServiceSpec spec = parseSpec(yaml);
    assertThat(manifestIds(spec)).containsExactly("app_manifest");
    assertThat(spec.getArtifacts().getPrimary().getPrimaryArtifactRef().getValue()).isEqualTo("app_artifact");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testBuildServiceYamlManifestOnlyWhenNoArtifact() throws Exception {
    OnboardingContextDTO context = githubManifestContext("app_manifest").build();

    String yaml = OnboardingServiceYamlBuilder.buildServiceYaml(
        context, "svc_id", "svc name", manifestNodes(context, MANIFEST_CONNECTOR_REF), null);

    KubernetesServiceSpec spec = parseSpec(yaml);
    assertThat(manifestIds(spec)).containsExactly("app_manifest");
    assertThat(spec.getArtifacts()).isNull();
  }

  // ---- manifest path handling (built by the provisioner, carried through the assembled YAML) ----

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testBuildServiceYamlCarriesSplitAndTrimmedManifestPaths() throws Exception {
    OnboardingContextDTO context = githubManifestContext("app_manifest").manifestPaths("a.yaml, ,b.yaml,").build();

    String yaml = OnboardingServiceYamlBuilder.buildServiceYaml(
        context, "svc_id", "svc name", manifestNodes(context, MANIFEST_CONNECTOR_REF), null);

    KubernetesServiceSpec spec = parseSpec(yaml);
    io.harness.cdng.manifest.yaml.kinds.K8sManifest manifest =
        (io.harness.cdng.manifest.yaml.kinds.K8sManifest) spec.getManifests().get(0).getManifest().getSpec();
    io.harness.cdng.manifest.yaml.GithubStore store =
        (io.harness.cdng.manifest.yaml.GithubStore) manifest.getStore().getValue().getSpec();
    assertThat(store.getPaths().getValue()).containsExactly("a.yaml", "b.yaml");
  }

  // ---- mergeServiceYaml (update) ----

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testMergeServiceYamlPreservesUntouchedManifest() throws Exception {
    // Existing service carries "existing_manifest"; the update targets a different manifest id.
    OnboardingContextDTO existingContext = githubManifestContext("existing_manifest").build();
    String existingYaml = OnboardingServiceYamlBuilder.buildServiceYaml(
        existingContext, "svc_id", "svc name", manifestNodes(existingContext, MANIFEST_CONNECTOR_REF), null);

    OnboardingContextDTO update = githubManifestContext("new_manifest").build();
    String mergedYaml = OnboardingServiceYamlBuilder.mergeServiceYaml(
        existingYaml, update, manifestNodes(update, MANIFEST_CONNECTOR_REF), null);

    KubernetesServiceSpec spec = parseSpec(mergedYaml);
    // The untouched existing manifest survives and the new one is appended.
    assertThat(manifestIds(spec)).containsExactlyInAnyOrder("existing_manifest", "new_manifest");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testMergeServiceYamlReplacesManifestWithSameIdentifier() throws Exception {
    OnboardingContextDTO existingContext = githubManifestContext("shared_manifest").build();
    String existingYaml = OnboardingServiceYamlBuilder.buildServiceYaml(
        existingContext, "svc_id", "svc name", manifestNodes(existingContext, MANIFEST_CONNECTOR_REF), null);

    OnboardingContextDTO update = githubManifestContext("shared_manifest").manifestBranch("release").build();
    String mergedYaml = OnboardingServiceYamlBuilder.mergeServiceYaml(
        existingYaml, update, manifestNodes(update, MANIFEST_CONNECTOR_REF), null);

    KubernetesServiceSpec spec = parseSpec(mergedYaml);
    // Same identifier => replaced in place, not duplicated.
    assertThat(manifestIds(spec)).containsExactly("shared_manifest");
    io.harness.cdng.manifest.yaml.kinds.K8sManifest manifest =
        (io.harness.cdng.manifest.yaml.kinds.K8sManifest) spec.getManifests().get(0).getManifest().getSpec();
    io.harness.cdng.manifest.yaml.GithubStore store =
        (io.harness.cdng.manifest.yaml.GithubStore) manifest.getStore().getValue().getSpec();
    assertThat(store.getBranch().getValue()).isEqualTo("release");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testMergeServiceYamlRejectsNonKubernetesService() throws Exception {
    // A service YAML with no (Kubernetes) service definition cannot be updated by onboarding.
    NGServiceConfig bare =
        NGServiceConfig.builder()
            .ngServiceV2InfoConfig(NGServiceV2InfoConfig.builder().identifier("svc_id").name("svc").build())
            .build();
    String bareYaml = YamlPipelineUtils.getYamlString(bare);

    OnboardingContextDTO update = githubManifestContext("app_manifest").build();
    assertThatThrownBy(()
                           -> OnboardingServiceYamlBuilder.mergeServiceYaml(
                               bareYaml, update, manifestNodes(update, MANIFEST_CONNECTOR_REF), null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Kubernetes service");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testMergeServiceYamlRejectsUnparseableYaml() {
    OnboardingContextDTO update = githubManifestContext("app_manifest").build();
    assertThatThrownBy(()
                           -> OnboardingServiceYamlBuilder.mergeServiceYaml(
                               "\t not: [valid", update, manifestNodes(update, MANIFEST_CONNECTOR_REF), null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testMergeServiceYamlOverlaysArtifactAndPreservesManifest() throws Exception {
    OnboardingContextDTO existingContext = githubManifestContext("app_manifest").build();
    String existingYaml = OnboardingServiceYamlBuilder.buildServiceYaml(
        existingContext, "svc_id", "svc name", manifestNodes(existingContext, MANIFEST_CONNECTOR_REF), null);

    OnboardingContextDTO update =
        OnboardingContextDTO.builder().artifactId("app_artifact").artifactImagePath("library/nginx").build();
    String mergedYaml = OnboardingServiceYamlBuilder.mergeServiceYaml(
        existingYaml, update, null, artifactNode(update, ARTIFACT_CONNECTOR_REF));

    KubernetesServiceSpec spec = parseSpec(mergedYaml);
    // Manifest preserved, artifact overlaid.
    assertThat(manifestIds(spec)).containsExactly("app_manifest");
    assertThat(spec.getArtifacts().getPrimary().getPrimaryArtifactRef().getValue()).isEqualTo("app_artifact");
  }
}
