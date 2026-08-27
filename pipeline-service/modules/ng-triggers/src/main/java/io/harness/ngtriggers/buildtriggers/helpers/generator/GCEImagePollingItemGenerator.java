/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.delegate.task.artifacts.gcp.GCEImageFilter;
import io.harness.delegate.task.artifacts.gcp.GCEImageLabel;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.polling.contracts.GCEImageFilterPayload;
import io.harness.polling.contracts.GCEImageLabelPayload;
import io.harness.polling.contracts.GCEImagePayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(CDC)
public class GCEImagePollingItemGenerator implements PollingItemGenerator {
  @Inject BuildTriggerHelper buildTriggerHelper;

  @Override
  public PollingItem generatePollingItem(
      BuildTriggerOpsData buildTriggerOpsData, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();

    PollingItem.Builder builder =
        getBaseInitializedPollingItem(ngTriggerEntity, buildTriggerOpsData, scopeInfo, isParentIdQueryingEnabled);
    String connectorKey;
    String versionRegexKey;

    if (HarnessYamlVersion.isV1(ngTriggerEntity.getHarnessVersion())) {
      connectorKey = Constants.CONNECTOR;
      versionRegexKey = Constants.VERSION_REGEX;
    } else {
      connectorKey = "spec.connectorRef";
      versionRegexKey = "spec.versionRegex";
    }
    String connectorRef = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, connectorKey);
    String versionRegex = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, versionRegexKey);
    String project = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.project");
    List<GCEImageLabel> labels =
        buildTriggerHelper.validateAndFetchGCEImageLabelsListFromJsonNode(buildTriggerOpsData, "spec.labels");
    List<GCEImageFilter> filters =
        buildTriggerHelper.validateAndFetchGCEImageFiltersListFromJsonNode(buildTriggerOpsData, "spec.filters");

    // Normalize versionRegex: "*" is not a valid regex pattern, use ".*" instead
    // Also handle empty/null versionRegex by defaulting to ".*" to match all images
    if (versionRegex == null || versionRegex.isEmpty() || "*".equals(versionRegex)) {
      versionRegex = ".*";
    }

    return builder
        .setPollingPayloadData(PollingPayloadData.newBuilder()
                                   .setConnectorRef(connectorRef)
                                   .setType(Type.GCE_IMAGE)
                                   .setGceImagePayload(GCEImagePayload.newBuilder()
                                                           .setProject(project)
                                                           .addAllLabels(mapToGCEImageLabelPayload(labels))
                                                           .addAllFilters(mapToGCEImageFilterPayload(filters))
                                                           .setVersionRegex(versionRegex)
                                                           .build())
                                   .build())
        .build();
  }

  public List<GCEImageLabelPayload> mapToGCEImageLabelPayload(List<GCEImageLabel> labels) {
    List<GCEImageLabelPayload> labelsPayload = new ArrayList<>();

    for (GCEImageLabel label : labels) {
      String name = label.getName();
      String value = label.getValue();

      GCEImageLabelPayload labelPayload = GCEImageLabelPayload.newBuilder().setName(name).setValue(value).build();

      labelsPayload.add(labelPayload);
    }

    return labelsPayload;
  }

  public List<GCEImageFilterPayload> mapToGCEImageFilterPayload(List<GCEImageFilter> filters) {
    List<GCEImageFilterPayload> filtersPayload = new ArrayList<>();

    for (GCEImageFilter filter : filters) {
      String name = filter.getName();
      String value = filter.getValue();

      GCEImageFilterPayload filterPayload = GCEImageFilterPayload.newBuilder().setName(name).setValue(value).build();

      filtersPayload.add(filterPayload);
    }

    return filtersPayload;
  }
}
