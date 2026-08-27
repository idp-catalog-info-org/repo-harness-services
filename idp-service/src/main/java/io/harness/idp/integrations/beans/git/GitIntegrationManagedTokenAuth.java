/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.beans.git;

import static io.harness.idp.integrations.utils.Constants.IDP_GIT_INTEGRATION_MANAGED_HCR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.IDP)
public class GitIntegrationManagedTokenAuth extends GitIntegrationAuth {
  @NotNull private String managedTokenSecretIdentifier = IDP_GIT_INTEGRATION_MANAGED_HCR;
}
