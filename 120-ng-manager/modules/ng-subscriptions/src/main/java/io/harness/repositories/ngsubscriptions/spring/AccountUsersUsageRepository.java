/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.ngsubscriptions.spring;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngsubscriptions.entity.DailyAccountUsers;
import io.harness.repositories.ngsubscriptions.custom.DailyAccountUsersCustomRepository;
import io.harness.spec.server.ng.v1.model.ModuleType;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

@OwnedBy(PL)
@HarnessRepo
public interface AccountUsersUsageRepository
    extends PagingAndSortingRepository<DailyAccountUsers, String>, CrudRepository<DailyAccountUsers, String>,
            DailyAccountUsersCustomRepository {
  Optional<DailyAccountUsers> findByAccountIdentifierAndModuleTypeAndYearAndMonthAndDay(
      String accountIdentifier, ModuleType moduleType, int year, int month, int day);
  List<DailyAccountUsers> findByAccountIdentifierAndModuleTypeAndYearAndMonth(
      String accountIdentifier, ModuleType moduleType, int year, int month);
}
