/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.app.beans.entities.EnvironmentEntity;
import io.harness.app.beans.entities.EnvironmentGroupEntity;
import io.harness.beans.IdentifierRef;
import io.harness.cd.beans.outcomes.EnvGroupOutcome;
import io.harness.cd.beans.outcomes.EnvironmentOutcome;
import io.harness.ci.cd.service.EnvironmentGroupService;
import io.harness.envgroup.remote.EnvironmentGroupResourceClient;
import io.harness.envgroup.unified.UnifiedEnvGroupResponseDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.infrastructure.unified.UnifiedEnvironmentConverterResponseDTO;
import io.harness.infrastructure.unified.UnifiedInfraConvertorResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

@Singleton
@OwnedBy(HarnessTeam.CI)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class EnvOutcomeHelper {
  @Inject private EnvironmentGroupService environmentGroupService;
  @Inject private EnvironmentGroupResourceClient environmentGroupResourceClient;

  public EnvironmentOutcome getEnvironmentOutcome(
      String envRef, EnvironmentEntity environmentEntity, String envGroupRefValue) {
    EnvGroupOutcome envGroupOutcome = null;
    if (isNotEmpty(envGroupRefValue)) {
      envGroupOutcome = getEnvGroupOutcome(environmentEntity, envGroupRefValue);
    }
    return EnvironmentOutcome.builder()
        .identifier(environmentEntity.getIdentifier())
        .ref(envRef)
        .name(environmentEntity.getName())
        .description(environmentEntity.getDescription())
        .tags(convertToMap(environmentEntity.getTags()))
        .type(environmentEntity.getType())
        .group(envGroupOutcome)
        .build();
  }

  public EnvironmentOutcome getEnvironmentOutcomeFromNGInfraResponse(
      String envRef, UnifiedInfraConvertorResponse infraEntityNgResponse, IdentifierRef envGroupIdentifierRef) {
    if (infraEntityNgResponse.getResponseDTO() != null
        && infraEntityNgResponse.getResponseDTO().getEnvironmentResponse() != null) {
      EnvGroupOutcome envGroupOutcome = null;
      if (envGroupIdentifierRef != null) {
        envGroupOutcome = getEnvGroupOutcomeFromNG(envGroupIdentifierRef);
      }

      UnifiedEnvironmentConverterResponseDTO environmentResponse =
          infraEntityNgResponse.getResponseDTO().getEnvironmentResponse();
      return EnvironmentOutcome.builder()
          .ref(envRef)
          .identifier(environmentResponse.getIdentifier())
          .name(environmentResponse.getName())
          .description(environmentResponse.getDescription())
          .tags(environmentResponse.getTags())
          .type(environmentResponse.getType())
          .group(envGroupOutcome)
          .build();
    }

    return EnvironmentOutcome.builder().ref(envRef).build();
  }

  public EnvironmentOutcome getEnvironmentOutcomeFromNGEnv(
      String envRef, EnvironmentEntity environmentEntity, IdentifierRef envGroupIdentifierRef) {
    EnvGroupOutcome envGroupOutcome = null;
    if (envGroupIdentifierRef != null) {
      envGroupOutcome = getEnvGroupOutcomeFromNG(envGroupIdentifierRef);
    }

    return EnvironmentOutcome.builder()
        .ref(envRef)
        .identifier(environmentEntity.getIdentifier())
        .name(environmentEntity.getName())
        .description(environmentEntity.getDescription())
        .tags(convertToMap(environmentEntity.getTags()))
        .type(environmentEntity.getType())
        .group(envGroupOutcome)
        .build();
  }

  private EnvGroupOutcome getEnvGroupOutcome(EnvironmentEntity environmentEntity, String envGroupRefValue) {
    EnvGroupOutcome envGroupOutcome;
    Optional<EnvironmentGroupEntity> environmentGroupEntityOp =
        environmentGroupService.get(environmentEntity.getAccountId(), environmentEntity.getOrgIdentifier(),
            environmentEntity.getProjectIdentifier(), envGroupRefValue);
    if (environmentGroupEntityOp.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Environment Group with identifier: [%s] is not found in project: [%s] org: [%s]",
              envGroupRefValue, environmentEntity.getProjectIdentifier(), environmentEntity.getOrgIdentifier()));
    }
    EnvironmentGroupEntity envGroupEntity = environmentGroupEntityOp.get();
    envGroupOutcome = EnvGroupOutcome.builder()
                          .id(envGroupEntity.getId())
                          .ref(envGroupRefValue)
                          .name(envGroupEntity.getName())
                          .description(envGroupEntity.getDescription())
                          .tags(convertToMap(envGroupEntity.getTags()))
                          .build();
    return envGroupOutcome;
  }

  private EnvGroupOutcome getEnvGroupOutcomeFromNG(IdentifierRef envGroupIdentifierRef) {
    EnvGroupOutcome envGroupOutcome;
    String envGroupRefValue = envGroupIdentifierRef.getIdentifier();
    UnifiedEnvGroupResponseDTO envGroupResponse = getResponse(environmentGroupResourceClient.getUnifiedEnvironmentGroup(
        envGroupRefValue, envGroupIdentifierRef.getAccountIdentifier(), envGroupIdentifierRef.getOrgIdentifier(),
        envGroupIdentifierRef.getProjectIdentifier()));
    if (envGroupResponse == null) {
      throw new InvalidRequestException(
          String.format("Could not find environment group with ref: [%s] as mentioned in stage", envGroupRefValue));
    }
    envGroupOutcome = EnvGroupOutcome.builder()
                          .ref(envGroupRefValue)
                          .id(envGroupResponse.getId())
                          .name(envGroupResponse.getName())
                          .description(envGroupResponse.getDescription())
                          .tags(envGroupResponse.getTags())
                          .build();
    return envGroupOutcome;
  }
}
