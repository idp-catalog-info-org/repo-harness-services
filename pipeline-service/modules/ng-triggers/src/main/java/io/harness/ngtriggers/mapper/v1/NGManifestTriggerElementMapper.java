/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.mapper.v1;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.source.artifact.HelmManifestSpec;
import io.harness.ngtriggers.beans.source.artifact.ManifestTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.store.BuildStore;
import io.harness.ngtriggers.beans.source.artifact.store.BuildStoreType;
import io.harness.ngtriggers.beans.source.artifact.store.BuildStoreTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.store.GcsBuildStoreTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.store.HttpBuildStoreTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.store.S3BuildStoreTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.version.HelmVersion;
import io.harness.ngtriggers.beans.source.webhook.ManifestType;

import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(HarnessTeam.PIPELINE)
public class NGManifestTriggerElementMapper {
  ManifestType toManifestTriggerType(io.harness.ngtriggers.beans.source.v1.artifact.ManifestType typeEnum) {
    switch (typeEnum) {
      case HELM_MANIFEST:
        return ManifestType.HELM_MANIFEST;
      default:
        throw new InvalidRequestException("Manifest Trigger Type " + typeEnum + " is invalid");
    }
  }
  ManifestTypeSpec toManifestTypeSpec(io.harness.ngtriggers.beans.source.v1.artifact.ManifestTriggerConfig spec) {
    switch (spec.getType()) {
      case HELM_MANIFEST:
        io.harness.ngtriggers.beans.source.v1.artifact.HelmManifestSpec helmManifestSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.HelmManifestSpec) spec.getSpec();
        return HelmManifestSpec.builder()
            .helmVersion(toHelmVersion(helmManifestSpec.getHelm_version()))
            .chartVersion(getChartNameAndVersionFromLocation(helmManifestSpec.getChart()).getLeft())
            .chartName(getChartNameAndVersionFromLocation(helmManifestSpec.getChart()).getRight())
            .store(toBuildStore(helmManifestSpec.getStore()))
            .eventConditions(helmManifestSpec.getEvent_conditions())
            .build();
      default:
        throw new InvalidRequestException("Manifest Trigger Type " + spec.getType() + " is invalid");
    }
  }

  HelmVersion toHelmVersion(io.harness.ngtriggers.beans.source.v1.artifact.version.HelmVersion helmVersionEnum) {
    switch (helmVersionEnum) {
      case v2:
        return HelmVersion.V2;
      case v3:
        return HelmVersion.V3;
      case v380:
        return HelmVersion.V380;
      case v4:
        return HelmVersion.V4;
      default:
        throw new InvalidRequestException("Helm Version " + helmVersionEnum + " is invalid");
    }
  }

  BuildStore toBuildStore(io.harness.ngtriggers.beans.source.v1.artifact.store.BuildStore buildStore) {
    return BuildStore.builder().type(toBuildStoreType(buildStore.getType())).spec(toBuildStoreSpec(buildStore)).build();
  }

  BuildStoreType toBuildStoreType(io.harness.ngtriggers.beans.source.v1.artifact.store.BuildStoreType typeEnum) {
    switch (typeEnum) {
      case S3:
        return BuildStoreType.S3;
      case GCS:
        return BuildStoreType.GCS;
      case HTTP:
        return BuildStoreType.HTTP;
      default:
        throw new InvalidRequestException("Build store type " + typeEnum + " is invalid");
    }
  }

  Pair<String, String> getBucketNameAndFolderPathFromLocation(String location) {
    String[] strings = location.split(":");
    return Pair.of(strings[0], strings[1]);
  }

  Pair<String, String> getChartNameAndVersionFromLocation(String location) {
    String[] strings = location.split("@");
    return Pair.of(strings[0], strings[1]);
  }

  BuildStoreTypeSpec toBuildStoreSpec(io.harness.ngtriggers.beans.source.v1.artifact.store.BuildStore buildStore) {
    switch (buildStore.getType()) {
      case HTTP:
        return HttpBuildStoreTypeSpec.builder().connectorRef(buildStore.fetchConnectorRef()).build();
      case GCS:
        io.harness.ngtriggers.beans.source.v1.artifact.store.GcsBuildStoreTypeSpec gcsBuildStoreSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.store.GcsBuildStoreTypeSpec) buildStore.getSpec();
        return GcsBuildStoreTypeSpec.builder()
            .bucketName(getBucketNameAndFolderPathFromLocation(gcsBuildStoreSpec.getLocation()).getLeft())
            .connectorRef(buildStore.fetchConnectorRef())
            .folderPath(getBucketNameAndFolderPathFromLocation(gcsBuildStoreSpec.getLocation()).getRight())
            .build();
      case S3:
        io.harness.ngtriggers.beans.source.v1.artifact.store.S3BuildStoreTypeSpec s3BuildStoreSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.store.S3BuildStoreTypeSpec) buildStore.getSpec();
        return S3BuildStoreTypeSpec.builder()
            .bucketName(getBucketNameAndFolderPathFromLocation(s3BuildStoreSpec.getLocation()).getLeft())
            .connectorRef(buildStore.fetchConnectorRef())
            .folderPath(getBucketNameAndFolderPathFromLocation(s3BuildStoreSpec.getLocation()).getRight())
            .region(s3BuildStoreSpec.getRegion())
            .build();
      default:
        throw new InvalidRequestException("Build store type " + buildStore.getType() + " is invalid");
    }
  }
}
