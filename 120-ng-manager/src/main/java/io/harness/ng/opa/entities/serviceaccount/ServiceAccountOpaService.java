/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.opa.entities.serviceaccount;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.governance.GovernanceMetadata;
import io.harness.serviceaccount.ServiceAccountDTO;

@OwnedBy(PL)
public interface ServiceAccountOpaService {
  GovernanceMetadata evaluatePoliciesWithEntity(String accountId, ServiceAccountDTO serviceAccountDTO,
      String orgIdentifier, String projectIdentifier, String action, String identifier);
}
