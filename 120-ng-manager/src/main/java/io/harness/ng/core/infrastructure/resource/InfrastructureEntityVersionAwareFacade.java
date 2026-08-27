/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.resource;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.services.impl.InfrastructureYamlSchemaHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@NextGenManagerAuth
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Singleton
public class InfrastructureEntityVersionAwareFacade {
  private InfrastructureYamlSchemaHelper infrastructureYamlSchemaHelper;

  public boolean validateSchema(String accountId, String yaml, String yamlVersion) {
    switch (yamlVersion) {
      case HarnessYamlVersion.V0:
        infrastructureYamlSchemaHelper.validateSchema(accountId, yaml);
        return true;

      case HarnessYamlVersion.V1:
        return true;
      default:
        throw new InvalidRequestException("Schema validation is not implemented for yaml version: " + yamlVersion);
    }
  }
}
