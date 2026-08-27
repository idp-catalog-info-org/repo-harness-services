/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.ng.smp;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.smp.entities.SMPAuthInfo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
@OwnedBy(PL)
public interface SMPAuthInfoRepository
    extends PagingAndSortingRepository<SMPAuthInfo, String>, CrudRepository<SMPAuthInfo, String> {
  SMPAuthInfo findByAccountIdentifier(String accountIdentifier);
  SMPAuthInfo findBySmpAccountIdentifier(String smpAccountIdentifier);
}
