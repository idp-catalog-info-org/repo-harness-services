/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.onboarding.mapper.ArtifactProviderType;
import io.harness.ng.core.onboarding.mapper.ManifestProviderType;
import io.harness.ng.core.onboarding.provisioners.artifact.ArtifactoryArtifactProvisioner;
import io.harness.ng.core.onboarding.provisioners.artifact.DockerArtifactProvisioner;
import io.harness.ng.core.onboarding.provisioners.artifact.EcrArtifactProvisioner;
import io.harness.ng.core.onboarding.provisioners.artifact.HarnessSampleArtifactProvisioner;
import io.harness.ng.core.onboarding.provisioners.infra.K8sDirectInfraProvisioner;
import io.harness.ng.core.onboarding.provisioners.manifest.BitbucketManifestProvisioner;
import io.harness.ng.core.onboarding.provisioners.manifest.GithubManifestProvisioner;
import io.harness.ng.core.onboarding.provisioners.manifest.GitlabManifestProvisioner;
import io.harness.ng.core.onboarding.provisioners.manifest.HarnessCodeManifestProvisioner;
import io.harness.ng.core.onboarding.provisioners.spec.ArtifactProvisioner;
import io.harness.ng.core.onboarding.provisioners.spec.InfraProvisioner;
import io.harness.ng.core.onboarding.provisioners.spec.ManifestProvisioner;
import io.harness.ng.core.onboarding.services.KubeconfigOnboardingService;
import io.harness.ng.core.onboarding.services.OnboardingOrchestrationService;
import io.harness.ng.core.onboarding.services.impl.KubeconfigOnboardingServiceImpl;
import io.harness.ng.core.onboarding.services.impl.OnboardingOrchestrationImpl;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;

/**
 * Guice wiring for the onboarding orchestration flow. Registers the onboarding services and the flat per-type
 * provisioner registries (manifest / artifact / infrastructure) that {@link OnboardingOrchestrationImpl}
 * dispatches through. Each provisioner is keyed by its provider/type enum, so adding a new source is a single
 * {@code MapBinder} entry here plus its provisioner implementation — no coordinator change.
 */
@OwnedBy(HarnessTeam.CDC)
public class OnboardingModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(KubeconfigOnboardingService.class).to(KubeconfigOnboardingServiceImpl.class);
    bind(OnboardingOrchestrationService.class).to(OnboardingOrchestrationImpl.class);

    MapBinder<ManifestProviderType, ManifestProvisioner> manifestProvisioners =
        MapBinder.newMapBinder(binder(), ManifestProviderType.class, ManifestProvisioner.class);
    manifestProvisioners.addBinding(ManifestProviderType.GITHUB).to(GithubManifestProvisioner.class);
    manifestProvisioners.addBinding(ManifestProviderType.BITBUCKET).to(BitbucketManifestProvisioner.class);
    manifestProvisioners.addBinding(ManifestProviderType.GITLAB).to(GitlabManifestProvisioner.class);
    manifestProvisioners.addBinding(ManifestProviderType.HARNESS_CODE).to(HarnessCodeManifestProvisioner.class);

    MapBinder<ArtifactProviderType, ArtifactProvisioner> artifactProvisioners =
        MapBinder.newMapBinder(binder(), ArtifactProviderType.class, ArtifactProvisioner.class);
    artifactProvisioners.addBinding(ArtifactProviderType.DOCKER_REGISTRY).to(DockerArtifactProvisioner.class);
    artifactProvisioners.addBinding(ArtifactProviderType.ECR).to(EcrArtifactProvisioner.class);
    artifactProvisioners.addBinding(ArtifactProviderType.ARTIFACTORY).to(ArtifactoryArtifactProvisioner.class);
    artifactProvisioners.addBinding(ArtifactProviderType.HARNESS_ARTIFACT_SAMPLE)
        .to(HarnessSampleArtifactProvisioner.class);

    MapBinder<InfrastructureType, InfraProvisioner> infraProvisioners =
        MapBinder.newMapBinder(binder(), InfrastructureType.class, InfraProvisioner.class);
    infraProvisioners.addBinding(InfrastructureType.KUBERNETES_DIRECT).to(K8sDirectInfraProvisioner.class);
  }
}
