/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.conversion;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.conversion.beans.ConversionJobEntity;
import io.harness.pms.conversion.beans.ConversionStatus;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Custom repository methods for ConversionJobEntity.
 */
@OwnedBy(HarnessTeam.PIPELINE)
public interface ConversionJobRepositoryCustom {
  /**
   * Update conversion job entity with given criteria and update operations.
   *
   * @param criteria Update criteria
   * @param update Update operations
   * @return Updated ConversionJobEntity
   */
  ConversionJobEntity update(Criteria criteria, Update update);

  /**
   * Find a single conversion job matching criteria with given sort order.
   *
   * @param criteria Search criteria
   * @param sort Sort order
   * @return Optional ConversionJobEntity
   */
  Optional<ConversionJobEntity> findOne(Criteria criteria, Sort sort);

  /**
   * Find all conversion jobs matching criteria with given sort order.
   *
   * @param criteria Search criteria
   * @param sort Sort order
   * @return List of ConversionJobEntity
   */
  List<ConversionJobEntity> findAll(Criteria criteria, Sort sort);

  /**
   * Find latest conversion job for given account and status.
   *
   * @param accountId Account identifier
   * @param status Job status
   * @return Optional ConversionJobEntity
   */
  Optional<ConversionJobEntity> findLatestByAccountAndStatus(String accountId, ConversionStatus status);

  /**
   * Find all conversion jobs for given account with pagination.
   *
   * @param accountId Account identifier
   * @param limit Number of results to return
   * @param offset Offset for pagination
   * @return List of ConversionJobEntity
   */
  List<ConversionJobEntity> findByAccount(String accountId, int limit, int offset);
}
