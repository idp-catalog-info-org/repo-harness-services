/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;

import com.mongodb.client.result.UpdateResult;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.IDP)
public interface BackstageCatalogEntityRepositoryCustom {
  List<BackstageCatalogEntity> queryEntities(String kind, String type, List<String> owners, List<String> tags,
      List<String> lifecycle, String accountIdentifier, List<String> skipEntityUids);
  UpdateResult updateEntityIdentifier(String accountIdentifier, String entityIdentifier, String entityUid);
  Page<BackstageCatalogEntity> findAll(Criteria criteria, Pageable pageable);
  List<BackstageCatalogEntity> findAllByAccountIdentifierAndEntityUidIn(
      String accountIdentifier, List<String> entityUids);
}
