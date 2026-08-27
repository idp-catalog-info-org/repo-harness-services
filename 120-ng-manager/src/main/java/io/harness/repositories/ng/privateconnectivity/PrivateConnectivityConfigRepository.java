/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.ng.privateconnectivity;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityOperationType;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityStatus;
import io.harness.ng.privateconnectivity.entities.PrivateConnectivityConfig;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@HarnessRepo
@Transactional
@OwnedBy(CI)
public interface PrivateConnectivityConfigRepository extends CrudRepository<PrivateConnectivityConfig, String> {
  Optional<PrivateConnectivityConfig> findByAccountIdentifier(String accountIdentifier);

  Optional<PrivateConnectivityConfig> findByProviderNetworkRef(String providerNetworkRef);

  Optional<PrivateConnectivityConfig> findByProviderNetworkName(String providerNetworkName);

  @Query("{ '$and': ["
      + "{ '$or': [ { 'nextRetryAt': null }, { 'nextRetryAt': { '$lte': ?0 } } ] },"
      + "{ '$or': ["
      + "{ 'status': { '$in': ?1 } },"
      + "{ 'status': ?2, 'operationType': ?3 },"
      + "{ 'status': ?2, 'operationType': { '$ne': ?3 }, "
      + "'providerNetworkRef': { '$type': 'string', '$ne': '' } },"
      + "{ 'status': ?2, 'operationType': ?4, "
      + "'providerNetworkName': { '$type': 'string', '$ne': '' } },"
      + "{ 'status': ?5, '$or': ["
      + "{ 'lastModifiedAt': { '$lte': ?6 } },"
      + "{ 'lastModifiedAt': null, 'createdAt': { '$lte': ?6 } }"
      + "] }"
      + "] }"
      + "] }")
  List<PrivateConnectivityConfig>
  findRecoverable(long now, Collection<PrivateConnectivityStatus> activeStatuses, PrivateConnectivityStatus errorStatus,
      PrivateConnectivityOperationType updateOperation, PrivateConnectivityOperationType provisionOperation,
      PrivateConnectivityStatus provisioningStatus, long staleBefore, Pageable pageable);
}
