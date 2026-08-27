/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.conversion;

import io.harness.annotation.HarnessRepo;
import io.harness.pms.conversion.beans.ConversionChecksum;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * Repository for ConversionChecksum.
 */
@HarnessRepo
public interface ConversionChecksumRepository
    extends PagingAndSortingRepository<ConversionChecksum, String>, CrudRepository<ConversionChecksum, String>,
            ConversionChecksumRepositoryCustom {}
