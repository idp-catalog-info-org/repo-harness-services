/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.opa.gitx.pipeline;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.governance.GovernanceMetadata;
import io.harness.metrics.service.api.MetricService;
import io.harness.opa.gitx.AbstractOpaOnSaveStatusHandler;
import io.harness.opa.gitx.OpaGitxStatusRepository;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

/** Pipeline-specific adapter; delegates OPA evaluation to {@link PipelineGovernanceService}. */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineOpaStatusHandler extends AbstractOpaOnSaveStatusHandler<PipelineEntity> {
  private final PipelineGovernanceService pipelineGovernanceService;
  private final OpaGitxStatusRepository repository;

  @Inject
  public PipelineOpaStatusHandler(PipelineGovernanceService pipelineGovernanceService,
      OpaGitxStatusRepository repository, @Named("OpaGitxStatusExecutor") Executor asyncExecutor,
      MetricService metricService) {
    super(asyncExecutor, metricService);
    this.pipelineGovernanceService = pipelineGovernanceService;
    this.repository = repository;
  }

  @Override
  protected GovernanceMetadata evaluateOpa(
      PipelineEntity entity, String action, ScopeInfo scopeInfo, String yamlForEvaluation) throws Exception {
    if (isEmpty(yamlForEvaluation)) {
      throw new InvalidRequestException(
          "Unable to evaluate governance policies: pipeline definition could not be resolved.");
    }
    return pipelineGovernanceService.validateGovernanceRules(scopeInfo.getAccountIdentifier(),
        scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), getBranch(entity), entity, yamlForEvaluation,
        action);
  }

  @Override
  protected OpaGitxStatusRepository getRepository() {
    return repository;
  }

  @Override
  protected String getRepoURL(PipelineEntity entity) {
    return entity.getRepoURL();
  }

  @Override
  protected String getEntityBranch(PipelineEntity entity) {
    return entity.getBranch();
  }

  @Override
  protected String getEntityType() {
    return "PIPELINES";
  }

  @Override
  protected String getServiceName() {
    return "pipeline-service";
  }
}
