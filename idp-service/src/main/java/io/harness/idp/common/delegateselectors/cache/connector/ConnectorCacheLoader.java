/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.delegateselectors.cache.connector;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCacheLoader;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.repositories.IntegrationEntityRepository;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class ConnectorCacheLoader implements DelegateSelectorsCacheLoader {
  IntegrationEntityRepository integrationEntityRepository;
  @Override
  public Map<String, Set<String>> load(String accountIdentifier) {
    Map<String, Set<String>> hostDelegateSelectors = new HashMap<>();
    List<IntegrationEntity> integrationEntities =
        integrationEntityRepository.findByAccountIdentifier(accountIdentifier);
    integrationEntities.forEach(integrationEntity -> {
      if (integrationEntity.getIntegration().equals(IntegrationEntity.Integration.GIT)) {
        if (((GitIntegrationEntity) integrationEntity).isExecuteOnDelegate()) {
          hostDelegateSelectors.put(((GitIntegrationEntity) integrationEntity).getHost(),
              ((GitIntegrationEntity) integrationEntity).getDelegateSelectors());
        }
      }
    });
    return hostDelegateSelectors;
  }
}
