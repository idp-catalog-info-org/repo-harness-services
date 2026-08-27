/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.v1.artifact;

import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.GOOGLE_ARTIFACT_REGISTRY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Value;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_TRIGGERS})
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.CDC)
@Value
public class GarSpec implements ArtifactTypeSpec {
  String connector;
  Conditions conditions;
  String version;
  String pkg;
  String region;
  String project;
  String repo;
  @Override
  public String fetchConnectorRef() {
    return connector;
  }

  @Override
  public String fetchBuildType() {
    return GOOGLE_ARTIFACT_REGISTRY;
  }

  @Override
  public List<TriggerEventDataCondition> fetchEventDataConditions() {
    return conditions != null ? conditions.getEvent() : null;
  }

  @Override
  public List<TriggerEventDataCondition> fetchMetaDataConditions() {
    return conditions != null ? conditions.getMetadata() : null;
  }

  @Override
  public String fetchJexlArtifactConditions() {
    return conditions != null ? conditions.getJexl() : null;
  }
}
