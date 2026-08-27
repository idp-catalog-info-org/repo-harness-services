/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils.polling;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ngtriggers.beans.source.NGTriggerType.ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MANIFEST;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MULTI_REGION_ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.WEBHOOK;

import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TriggerException;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GeneratorFactory;
import io.harness.ngtriggers.buildtriggers.helpers.generator.PollingItemGenerator;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.polling.contracts.Category;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.Qualifier;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class PollingSubscriptionHelper {
  private final BuildTriggerHelper buildTriggerHelper;
  private final NGTriggerElementMapper ngTriggerElementMapper;
  private final GeneratorFactory generatorFactory;

  public List<PollingItem> generatePollingItems(NGTriggerEntity ngTriggerEntity, boolean shouldFetchPipelineYaml,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerType triggerType = ngTriggerEntity.getType();
    if (triggerType != ARTIFACT && triggerType != MANIFEST && triggerType != WEBHOOK
        && triggerType != MULTI_REGION_ARTIFACT) {
      throw new InvalidRequestException(
          "Polling items generation is not support for trigger type " + triggerType.toString());
    }

    try {
      TriggerDetails triggerDetails =
          ngTriggerElementMapper.toTriggerDetails(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
      Optional<String> pipelineYml = Optional.ofNullable(null);
      // https://harness.atlassian.net/browse/PIPE-19108,
      // buildTriggerHelper.fetchResolvedTemplatesPipelineForTrigger(triggerDetails) throws an error(as it is not able
      // to fetch the pipeline). So to solve the above ticket we use shouldFetchPipelineYaml to not throw any exception
      // while retrieving pipelineYml.
      if (shouldFetchPipelineYaml) {
        pipelineYml = buildTriggerHelper.fetchResolvedTemplatesPipelineForTrigger(
            triggerDetails, scopeInfo, isParentIdQueryingEnabled);
        if (!pipelineYml.isPresent()) {
          throw new InvalidRequestException("Failed to retrieve pipeline");
        }
      }

      List<BuildTriggerOpsData> buildTriggerOpsData = new ArrayList<>();
      if (triggerType == ARTIFACT) {
        buildTriggerOpsData.add(
            buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(triggerDetails, pipelineYml.orElse(null)));
      } else if (triggerType == MANIFEST) {
        buildTriggerOpsData.add(
            buildTriggerHelper.generateBuildTriggerOpsDataForManifest(triggerDetails, pipelineYml.orElse(null)));
      } else if (triggerType == WEBHOOK) {
        buildTriggerOpsData.add(buildTriggerHelper.generateBuildTriggerOpsDataForGitPolling(triggerDetails));
      } else if (triggerType == MULTI_REGION_ARTIFACT) {
        buildTriggerOpsData.addAll(buildTriggerHelper.generateBuildTriggerOpsDataForMultiArtifact(triggerDetails));
      }

      List<PollingItem> pollingItems = new ArrayList<>();
      for (BuildTriggerOpsData buildTriggerOpsDataEntry : buildTriggerOpsData) {
        PollingItemGenerator pollingItemGenerator = null;
        pollingItemGenerator = generatorFactory.retrievePollingItemGenerator(buildTriggerOpsDataEntry);
        if (pollingItemGenerator != null) {
          pollingItems.add(
              pollingItemGenerator.generatePollingItem(buildTriggerOpsDataEntry, scopeInfo, isParentIdQueryingEnabled));
        } else {
          throw new InvalidRequestException("No polling item generator found for Trigger "
              + TriggerHelper.getTriggerRef(ngTriggerEntity) + " with specMap "
              + buildTriggerOpsDataEntry.getTriggerSpecMap());
        }
      }
      return pollingItems;
    } catch (Exception e) {
      String msg = String.format(
          "Failed while generating Polling Item for Trigger : %s", TriggerHelper.getTriggerRef(ngTriggerEntity));
      log.error(msg, e);
      throw new TriggerException(msg, e, USER_SRE);
    }
  }

  public List<PollingItem> generateMultiArtifactPollingItemsToUnsubscribe(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    List<String> signatures = ngTriggerEntity.getMetadata().getSignatures();
    if (isEmpty(signatures)) {
      return Collections.emptyList();
    }
    return signatures.stream()
        .map(signature -> {
          PollingItem.Builder pollingItem = PollingItem.newBuilder();
          pollingItem.setCategory(Category.ARTIFACT)
              .setQualifier(
                  Qualifier.newBuilder()
                      .setAccountId(ngTriggerEntity.getAccountId())
                      .setOrganizationId(
                          isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier())
                      .setProjectId(isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier()
                                                              : ngTriggerEntity.getProjectIdentifier())
                      .setParentUniqueId(emptyIfNull(
                          isParentIdQueryingEnabled ? scopeInfo.getUniqueId() : ngTriggerEntity.getParentUniqueId()))
                      .build())
              .setSignature(signature);
          return pollingItem.build();
        })
        .collect(Collectors.toList());
  }
}
