/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.template.TemplateReferenceRequestDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.mappers.dto.PMSPipelineDtoMapper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.templatereference.enums.ReferrerEntityType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class PipelineTemplateReferenceHelper {
  private final TemplateResourceClient templateResourceClient;

  /**
   * Delete template references for a pipeline.
   * This is a non-blocking operation - failures are logged but don't affect the main pipeline delete operation.
   */
  public void deleteTemplateReferencesForPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PipelineEntity pipelineEntity) {
    try {
      // Extract Git details from pipeline entity
      EntityGitDetails gitDetails = PMSPipelineDtoMapper.getEntityGitDetails(pipelineEntity);

      // Delete template references via HTTP client
      NGRestUtils.getResponse(templateResourceClient.deleteEntityTemplateReferences(pipelineIdentifier,
          ReferrerEntityType.PIPELINE.name(), orgIdentifier, projectIdentifier,
          gitDetails != null ? gitDetails.getRepoIdentifier() : null,
          gitDetails != null ? gitDetails.getBranch() : null, gitDetails != null ? gitDetails.getFilePath() : null,
          accountId, gitDetails != null ? gitDetails.getObjectId() : null,
          gitDetails != null ? gitDetails.getRepoName() : null, gitDetails != null ? gitDetails.getCommitId() : null,
          gitDetails != null ? gitDetails.getFileUrl() : null, gitDetails != null ? gitDetails.getRepoUrl() : null,
          gitDetails != null ? gitDetails.getRootFolder() : null,
          gitDetails != null ? gitDetails.getParentEntityConnectorRef() : null,
          gitDetails != null ? gitDetails.getParentEntityRepoName() : null,
          gitDetails != null ? gitDetails.getIsHarnessCodeRepo() : null));

      log.debug("Successfully deleted template references for pipeline [{}]", pipelineIdentifier);
    } catch (Exception e) {
      // Non-blocking: log error but don't fail the pipeline delete operation
      log.warn("Failed to delete template references for pipeline [{}]: {}", pipelineIdentifier, e.getMessage(), e);
    }
  }

  /**
   * Extract and upsert template references for a pipeline.
   * This is a non-blocking operation - failures are logged but don't affect the main pipeline operation.
   */
  public void upsertTemplateReferencesForV1Pipeline(
      PipelineEntity pipelineEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    try {
      String accountId = isParentIdQueryingEnabled ? scopeInfo.getAccountIdentifier() : pipelineEntity.getAccountId();
      String orgIdentifier =
          isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : pipelineEntity.getOrgIdentifier();
      String projectIdentifier =
          isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : pipelineEntity.getProjectIdentifier();

      if (isEmpty(pipelineEntity.getYaml())) {
        log.debug(
            "Pipeline [{}] has empty YAML, skipping template reference extraction", pipelineEntity.getIdentifier());
        return;
      }

      // Extract Git details from pipeline entity
      EntityGitDetails gitDetails = PMSPipelineDtoMapper.getEntityGitDetails(pipelineEntity);

      // Build request DTO with YAML and gitDetails (using DTO prevents JSON encoding issues with String body)
      TemplateReferenceRequestDTO request = TemplateReferenceRequestDTO.builder()
                                                .yaml(pipelineEntity.getYaml())
                                                .yamlVersion(HarnessYamlVersion.V1)
                                                .gitDetails(gitDetails)
                                                .build();

      // Call template-service to extract, enrich, and upsert template references from YAML
      NGRestUtils.getResponse(templateResourceClient.upsertEntityTemplateReferencesFromYaml(accountId, orgIdentifier,
          projectIdentifier, pipelineEntity.getIdentifier(), ReferrerEntityType.PIPELINE.name(), request));

      log.debug("Successfully upserted template references for pipeline [{}]", pipelineEntity.getIdentifier());
    } catch (Exception e) {
      // Non-blocking: log error but don't fail the pipeline operation
      log.warn("Failed to upsert template references for pipeline [{}]: {}", pipelineEntity.getIdentifier(),
          e.getMessage(), e);
    }
  }
}
