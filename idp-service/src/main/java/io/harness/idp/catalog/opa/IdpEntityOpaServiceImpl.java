/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.opa;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.governance.GovernanceMetadata;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.opa.OpaService;
import io.harness.opaclient.model.OpaConstants;

import com.google.inject.Inject;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class IdpEntityOpaServiceImpl implements IdpEntityOpaService {
  private OpaService opaService;

  @Override
  public GovernanceMetadata evaluatePoliciesWithEntity(CatalogEntity entity, String action) {
    try {
      Map<String, Object> payload = IdpEntityOpaPayloadMapper.buildPayload(entity);

      IdpEntityOpaEvaluationContext context = IdpEntityOpaEvaluationContext.builder().idpEntity(payload).build();

      return opaService.evaluate(context, entity.getAccountIdentifier(), entity.getOrgIdentifier(),
          entity.getProjectIdentifier(), entity.getIdentifier(), action, OpaConstants.OPA_EVALUATION_TYPE_IDP_ENTITY);
    } catch (Exception ex) {
      log.error("Error evaluating OPA policies for IDP entity: kind={}, identifier={}", entity.getKind(),
          entity.getIdentifier(), ex);
      throw new InvalidRequestException("Error evaluating OPA policies for IDP entity: " + ex.getMessage(), ex);
    }
  }
}
