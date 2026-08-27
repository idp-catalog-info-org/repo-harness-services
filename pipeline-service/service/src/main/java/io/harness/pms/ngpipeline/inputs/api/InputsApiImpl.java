/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputs.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.exception.EntityNotFoundException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.ngpipeline.inputs.beans.entity.InputEntity;
import io.harness.pms.ngpipeline.inputs.helper.CloneRefRuntimeInputHelper;
import io.harness.pms.ngpipeline.inputs.mappers.PMSInputsElementMapper;
import io.harness.pms.ngpipeline.inputs.service.PMSInputsService;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.helpers.validate.ValidateAndMergeHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.spec.server.pipeline.v1.InputsApi;
import io.harness.spec.server.pipeline.v1.model.InputSetTemplateRequestBody;
import io.harness.spec.server.pipeline.v1.model.InputsResponseBody;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class InputsApiImpl implements InputsApi {
  private final PMSInputsService pmsInputsService;
  private final PMSPipelineService pmsPipelineService;
  private final ValidateAndMergeHelper validateAndMergeHelper;
  private final ScopeResolutionHelper scopeResolutionHelper;
  private final PMSPipelineServiceHelper pipelineServiceHelper;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response getPipelineInputs(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, InputSetTemplateRequestBody body, String loadFromCache,
      @AccountIdentifier String account, String branch, String repo, Boolean isHarnessCodeRepo, String connector) {
    log.info(String.format(
        "Retrieving inputs for pipeline %s in project %s, org %s, account %s", pipeline, project, org, account));
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder()
                                                 .branch(branch)
                                                 .connectorRef(connector)
                                                 .repoName(repo)
                                                 .isHarnessCodeRepo(isHarnessCodeRepo)
                                                 .build());
    boolean isParentIdQueryingEnabledForPipeline = pipelineServiceHelper.isParentIdQueryingEnabled(account);
    boolean isParentIdQueryingEnabledForInputSet = pipelineServiceHelper.isParentIdQueryingEnabledForInputSet(account);
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(account, org, project);
    Optional<PipelineEntity> optionalPipelineEntity = pmsPipelineService.getPipeline(
        account, org, project, pipeline, false, false, false, false, scopeInfo, isParentIdQueryingEnabledForPipeline);
    if (optionalPipelineEntity.isEmpty()) {
      throw new EntityNotFoundException(
          String.format("Pipeline with the given ID: %s does not exist or has been deleted", pipeline));
    }
    PipelineEntity pipelineEntity = optionalPipelineEntity.get();
    String pipelineYaml = pipelineEntity.getYaml();

    String resolvedPipelineYaml = null;
    if (HarnessYamlVersion.isV1(pipelineEntity.getHarnessVersion())) {
      // Pre-process pipeline YAML first time (adds IDs to stages and steps)
      // Similar to ExecutionHelper#getPipelineMetadataInternalDTO for V1 pipelines
      resolvedPipelineYaml = pipelineServiceHelper.preProcessPipelineYaml(pipelineYaml, false);

      // Resolve template refs in pipeline using the preprocessed YAML
      try {
        TemplateMergeResponseDTO templateMergeResponseDTO;
        String orgId = scopeInfo.getOrgIdentifier();
        String projectId = scopeInfo.getProjectIdentifier();
        templateMergeResponseDTO = pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity.getAccountId(),
            orgId, projectId, resolvedPipelineYaml, loadFromCache, pipelineEntity.getHarnessVersion());
        resolvedPipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
      } catch (Exception e) {
        log.warn("Cannot get resolved templates pipeline YAML", e);
      }

      // Injecting pipeline level clone runtime inputs if ref is not present in pipeline clone
      pipelineYaml = CloneRefRuntimeInputHelper.injectCloneRefAsRuntimeInput(pipelineYaml);

      // doing this to maintain the consistency between pipelineYaml and resolvedPipelineYaml
      resolvedPipelineYaml = CloneRefRuntimeInputHelper.injectCloneRefAsRuntimeInput(resolvedPipelineYaml);

      // Resolve pipeline with all templates runtime inputs
      // This removes original inputs and adds only generated template runtime inputs
      // It also replaces <+input> with <+inputs.<uuid>> in template with sections
      pipelineYaml = pipelineTemplateHelper.resolvePipelineWithAllTemplatesRuntimeInputs(
          pipelineYaml, account, org, project, loadFromCache);
      // Set resolvedPipelineYaml after resolving runtime inputs so it includes the inputs section
    }

    Optional<Map<String, InputEntity>> optionalInputEntityMap = pmsInputsService.get(pipelineYaml);
    if (optionalInputEntityMap.isEmpty()) {
      throw new IllegalStateException(String.format("Error in parsing inputs for pipeline %s", pipeline));
    }
    Map<String, InputEntity> inputEntityMap = optionalInputEntityMap.get();
    List<String> stageIdentifiers = body == null ? Collections.emptyList() : body.getStageIds();
    InputSetTemplateResponseDTOPMS response =
        validateAndMergeHelper.getInputSetTemplateResponseDTO(account, org, project, pipeline, stageIdentifiers,
            Boolean.parseBoolean(loadFromCache), scopeInfo, isParentIdQueryingEnabledForPipeline,
            isParentIdQueryingEnabledForInputSet, Optional.of(pipelineEntity), pipelineYaml);

    InputsResponseBody inputsResponseBody =
        PMSInputsElementMapper.inputsResponseDTOPMS(inputEntityMap, response, resolvedPipelineYaml);
    return Response.ok().entity(inputsResponseBody).build();
  }
}
