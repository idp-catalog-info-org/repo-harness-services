/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2024/10/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.encryption.DynamicSecretReferenceHelper;
import io.harness.encryption.SecretRefParsedData;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.utils.IdentifierRefHelper;

import java.util.Set;
import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Value
@UtilityClass
@Slf4j
@OwnedBy(PIPELINE)
@CodePulse(module = ProductModule.CDS, components = HarnessModuleComponent.CDS_PIPELINE, unitCoverageRequired = true)
public class SecretFunctorHelper {
  public void checkForAccess(String secretIdentifier, Ambiance ambiance, PipelineRbacHelper pipelineRbacHelper,
      DynamicSecretReferenceHelper dynamicSecretReferenceHelper) {
    IdentifierRef identifierRef;
    Set<EntityDetailProtoDTO> entityDetails;
    // If this is true then the secret is accessed via the path-reference to the secret that is present remotely. The
    // secret is not stored in harness as an entity.
    if (AmbianceUtils.checkIfFeatureFlagEnabled(
            ambiance, FeatureName.PIPE_DO_RBAC_CHECK_ON_SECRETS_FOR_PATH_REFERENCE.name())
        && dynamicSecretReferenceHelper.isSecretIdentifierAPathReference(secretIdentifier)) {
      SecretRefParsedData secretRefParsedData =
          dynamicSecretReferenceHelper.validateAndGetSecretRefParsedData(secretIdentifier);
      identifierRef = IdentifierRefHelper.getIdentifierRef(secretRefParsedData.getSecretManagerIdentifier(),
          AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
          AmbianceUtils.getProjectIdentifier(ambiance));
      entityDetails = Set.of(EntityDetailProtoDTO.newBuilder()
                                 .setType(EntityTypeProtoEnum.CONNECTORS)
                                 .setIdentifierRef(IdentifierRefProtoDTOHelper.fromIdentifierRef(identifierRef))
                                 .build());
    } else {
      identifierRef = IdentifierRefHelper.getSecretIdentifierRef(secretIdentifier, AmbianceUtils.getAccountId(ambiance),
          AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
      entityDetails = Set.of(EntityDetailProtoDTO.newBuilder()
                                 .setType(EntityTypeProtoEnum.SECRETS)
                                 .setIdentifierRef(IdentifierRefProtoDTOHelper.fromIdentifierRef(identifierRef))
                                 .build());
    }
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails);
  }
}
