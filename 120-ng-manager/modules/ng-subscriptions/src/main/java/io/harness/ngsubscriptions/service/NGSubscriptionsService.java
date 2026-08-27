/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsubscriptions.service;

import io.harness.ngsubscriptions.entity.ModuleAccess;
import io.harness.spec.server.ng.v1.model.DailyModuleAccountAccessDTO;
import io.harness.spec.server.ng.v1.model.ModuleType;
import io.harness.spec.server.ng.v1.model.PrincipalWithAccessFilter;
import io.harness.spec.server.ng.v1.model.PrincipalWithAccessResponse;
import io.harness.spec.server.ng.v1.model.SubscriptionUsageDTO;
import io.harness.spec.server.ng.v1.model.UpdateAccessRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface NGSubscriptionsService {
  List<SubscriptionUsageDTO> getSubscriptions(String accountIdentifier, int year);
  ModuleAccess updateModuleAccess(String accountIdentifier, UpdateAccessRequest updateAccessRequest);
  List<DailyModuleAccountAccessDTO> getModuleAccountAccessList(
      String accountIdentifier, ModuleType moduleType, Integer year, Integer month);
  PrincipalWithAccessResponse findPrincipals(String accountIdentifier, ModuleType moduleType);
  PrincipalWithAccessResponse findPrincipalsWithFilter(
      String accountIdentifier, ModuleType moduleType, PrincipalWithAccessFilter filter);
  Map<ModuleType, Boolean> getDev360ModuleAccessForAccountAndUser(
      String accountIdentifier, String userIdentifier, Set<String> moduleTypeInputSet);
}
