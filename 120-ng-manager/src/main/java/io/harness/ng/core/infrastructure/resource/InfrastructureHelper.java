/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.resource;

import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;
import static io.harness.validation.Validator.notEmptyCheck;
import static io.harness.validation.Validator.notNullCheck;

import static java.lang.String.format;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.infra.definition.config.InfrastructureDefinitionConfig;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
@Singleton
public class InfrastructureHelper {
  @Inject InfrastructureEntityService infrastructureEntityService;
  @Inject private AccessControlClient accessControlClient;
  @Inject private ScopeInfoService scopeResolverService;

  public IdentifierRef getConnectorRef(
      String accountId, String orgId, String projectId, String environmentId, String infrastructureDefinitionId) {
    notEmptyCheck("AccountId should be provided.", accountId);
    notEmptyCheck("EnvironmentId should be provided.", environmentId);
    notEmptyCheck("InfrastructureDefinitionId should be provided.", infrastructureDefinitionId);
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountId, orgId, projectId);

    InfrastructureEntity infraEntity =
        infrastructureEntityService
            .get(accountId, orgId, projectId, scopeInfo, environmentId, infrastructureDefinitionId)
            .orElse(null);
    notNullCheck(format("No infrastructure definition [%s] exists in the environment [%s].", infrastructureDefinitionId,
                     environmentId),
        infraEntity);

    InfrastructureDefinitionConfig infrastructureConfig =
        InfrastructureEntityConfigMapper.toInfrastructureConfig(infraEntity).getInfrastructureDefinitionConfig();
    String connectorRef = infrastructureConfig.getSpec().getConnectorReference().getValue();

    notEmptyCheck(
        format("Connector in the infrastructure definition [%s] is empty", infrastructureDefinitionId), connectorRef);
    return IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgId, projectId);
  }

  public void checkForAccessOrThrow(String accountId, String orgIdentifier, String projectIdentifier,
      String envIdentifier, String permission, String action) {
    String exceptionMessage = format("unable to %s infrastructure(s)", action);
    String[] environmentRefSplit = StringUtils.split(envIdentifier, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
    if (environmentRefSplit == null || environmentRefSplit.length == 1) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(NGResourceType.ENVIRONMENT, envIdentifier), permission, exceptionMessage);
    } else {
      IdentifierRef envIdentifierRef = IdentifierRefHelper.getIdentifierRefOrThrowException(
          envIdentifier, accountId, orgIdentifier, projectIdentifier, YAMLFieldNameConstants.ENVIRONMENT);
      accessControlClient.checkForAccessOrThrow(
          ResourceScope.of(envIdentifierRef.getAccountIdentifier(), envIdentifierRef.getOrgIdentifier(),
              envIdentifierRef.getProjectIdentifier()),
          Resource.of(NGResourceType.ENVIRONMENT, envIdentifierRef.getIdentifier()), permission, exceptionMessage);
    }
  }
}
