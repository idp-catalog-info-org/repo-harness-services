/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.accountdetails;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.AccountDTO;

@OwnedBy(HarnessTeam.IDP)
public interface AccountsDetailsCache {
  AccountDetailsDTO get(String accountIdentifier);
  void put(String accountIdentifier, AccountDetailsDTO accountDetailsDTO);
}
