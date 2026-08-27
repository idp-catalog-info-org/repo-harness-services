/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.dryrun.semantic.rules;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticConstants;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticRefUtils;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticRule;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticValidationContext;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineValidationResult;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Rule 1: every referenced connector must resolve. A CONNECTORS referred entity whose scoped ref is
 * absent from {@code connectorsByRef} did not resolve during the batch fetch and is flagged as an
 * ERROR. Harness Code codebases emit no connector referred entity (empty codebase connectorRef is
 * never emitted by filter creation), so there is nothing to special-case here. Templates are out of
 * scope in v1 (connectors only) -- template existence stays on the filter/plan-creation error path.
 */
@Singleton
@OwnedBy(PIPELINE)
public class ReferencedEntitiesExistRule implements SemanticRule {
  private static final String ENTITY_TYPE_CONNECTOR = "CONNECTOR";

  @Override
  public List<DryRunPipelineValidationResult> apply(SemanticValidationContext ctx) {
    List<DryRunPipelineValidationResult> findings = new ArrayList<>();
    if (ctx.getReferredEntities() == null) {
      return findings;
    }
    // When the batch connector fetch threw, connectorsByRef is empty for a transient reason, not
    // because the connectors are absent. Skip the existence check to stay fail-open (the validator
    // already emitted a WARNING) rather than flagging every referenced connector as missing.
    if (ctx.isConnectorFetchFailed()) {
      return findings;
    }
    for (EntityDetailProtoDTO entity : ctx.getReferredEntities()) {
      if (entity.getType() != EntityTypeProtoEnum.CONNECTORS) {
        continue;
      }
      String scopedRef = SemanticRefUtils.scopedRef(entity.getIdentifierRef());
      if (scopedRef == null) {
        continue;
      }
      if (!ctx.getConnectorsByRef().containsKey(scopedRef)) {
        findings.add(error(scopedRef));
      }
    }
    return findings;
  }

  private DryRunPipelineValidationResult error(String connectorRef) {
    DryRunPipelineValidationResult result = new DryRunPipelineValidationResult();
    result.setValidationType(SemanticConstants.VALIDATION_TYPE_SEMANTIC);
    result.setSeverity(SemanticConstants.SEVERITY_ERROR);
    result.setEntityType(ENTITY_TYPE_CONNECTOR);
    result.setEntityIdentifier(connectorRef);
    result.setErrorMessage("Referenced connector '" + connectorRef + "' could not be found.");
    result.setHint("Verify the connector exists and is accessible in the current scope.");
    return result;
  }
}
