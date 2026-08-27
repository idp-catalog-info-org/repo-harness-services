/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.events;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.audit.ResourceTypeConstants.IDP_GIT_INTEGRATIONS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@OwnedBy(IDP)
public class GitIntegrationUpdateEvent implements Event {
  public static final String GIT_INTEGRATION_UPDATED = "GIT_INTEGRATION_UPDATED";
  private String accountIdentifier;
  private GitIntegrationEntity oldEntity;
  private GitIntegrationEntity newEntity;

  public GitIntegrationUpdateEvent(
      String accountIdentifier, GitIntegrationEntity oldEntity, GitIntegrationEntity newEntity) {
    this.accountIdentifier = accountIdentifier;
    this.oldEntity = oldEntity;
    this.newEntity = newEntity;
  }

  @Override
  public ResourceScope getResourceScope() {
    return new AccountScope(accountIdentifier);
  }

  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, newEntity.getIdentifier());
    return Resource.builder()
        .identifier(newEntity.getAccountIdentifier() + "_" + newEntity.getIdentifier())
        .type(IDP_GIT_INTEGRATIONS)
        .labels(labels)
        .build();
  }

  @Override
  public String getEventType() {
    return GIT_INTEGRATION_UPDATED;
  }
}
