/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.delegate.beans.DelegateProfileDetailsNg;
import io.harness.delegate.filter.DelegateProfileFilterPropertiesDTO;

@OwnedBy(HarnessTeam.DEL)
public interface DelegateProfileManagerNgService {
  PageResponse<DelegateProfileDetailsNg> list(
      String accountId, PageRequest<DelegateProfileDetailsNg> pageRequest, String orgId, String projectId);

  PageResponse<DelegateProfileDetailsNg> listV2(String accountId, String orgId, String projectId,
      String filterIdentifier, String searchTerm, DelegateProfileFilterPropertiesDTO filterProperties,
      PageRequest<DelegateProfileDetailsNg> pageRequest);
}
