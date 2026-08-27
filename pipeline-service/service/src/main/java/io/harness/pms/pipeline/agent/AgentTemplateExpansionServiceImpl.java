/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipeline.agent;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.agent.expansion.AgentTemplateExpansionService;
import io.harness.agent.expansion.AgentTemplateProcessor;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.remote.TemplateResourceClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class AgentTemplateExpansionServiceImpl implements AgentTemplateExpansionService {
  private final TemplateResourceClient templateServiceClient;
  private final AgentTemplateProcessor agentTemplateProcessor;

  @Inject
  public AgentTemplateExpansionServiceImpl(
      TemplateResourceClient templateServiceClient, AgentTemplateProcessor agentTemplateProcessor) {
    this.templateServiceClient = templateServiceClient;
    this.agentTemplateProcessor = agentTemplateProcessor;
  }

  @Override
  public JsonNode expandAgentStep(
      String accountId, String orgId, String projectId, String templateId, Map<String, JsonNode> userInputs) {
    log.info("Expanding agent step template '{}' for account '{}' via applyTemplates API", templateId, accountId);

    String syntheticYaml = agentTemplateProcessor.buildSyntheticV1Yaml(templateId, userInputs);
    log.debug("Synthetic V1 YAML for agent template '{}': {}", templateId, syntheticYaml);

    GitSyncBranchContext emptyGitContext =
        GitSyncBranchContext.builder().gitBranchInfo(GitEntityInfo.builder().build()).build();

    try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(emptyGitContext, true)) {
      TemplateMergeResponseDTO mergeResponse;
      try {
        mergeResponse = callApplyTemplates(accountId, orgId, projectId, syntheticYaml);
      } catch (Exception ex) {
        throw new InvalidRequestException(
            String.format("Failed to expand agent template '%s' via applyTemplates: %s", templateId, ex.getMessage()),
            ex);
      }
      if (mergeResponse == null) {
        throw new InvalidRequestException(
            String.format("Agent template '%s' expansion returned empty response", templateId));
      }
      try {
        return agentTemplateProcessor.processExpandedTemplate(
            mergeResponse.getMergedPipelineYaml(), templateId, accountId, orgId, projectId, userInputs);
      } catch (InvalidRequestException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new InvalidRequestException(
            String.format("Failed to process agent template '%s': %s", templateId, ex.getMessage()), ex);
      }
    }
  }

  private TemplateMergeResponseDTO callApplyTemplates(
      String accountId, String orgId, String projectId, String syntheticYaml) {
    TemplateApplyRequestDTO requestDTO = TemplateApplyRequestDTO.builder()
                                             .originalEntityYaml(syntheticYaml)
                                             .checkForAccess(false)
                                             .getMergedYamlWithTemplateField(false)
                                             .yamlVersion(HarnessYamlVersion.V1)
                                             .build();
    return NGRestUtils.getResponse(templateServiceClient.applyTemplatesOnGivenYamlV2(
        accountId, orgId, projectId, null, null, null, null, null, null, null, null, "true", requestDTO, false));
  }
}
