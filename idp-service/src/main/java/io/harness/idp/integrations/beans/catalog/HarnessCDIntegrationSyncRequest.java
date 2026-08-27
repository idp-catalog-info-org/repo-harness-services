/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.beans.catalog;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.IDP)
public class HarnessCDIntegrationSyncRequest extends CatalogIntegrationSyncRequest {
  @NotNull private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private String scope;
  private String scopeUniqueId;
  @NotNull private String identifier;
  @NotNull private String action;
}
