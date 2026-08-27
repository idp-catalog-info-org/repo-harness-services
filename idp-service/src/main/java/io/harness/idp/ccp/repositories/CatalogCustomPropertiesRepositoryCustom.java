/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.ccp.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.mongodb.client.result.UpdateResult;
import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public interface CatalogCustomPropertiesRepositoryCustom {
  long deleteMulti(String accountIdentifier, List<String> entityRefs, String field);
  long deleteMulti(String accountIdentifier, String entityRef, List<String> fields);

  List<FieldAndCount> getCustomPropertiesFieldEntities(String accountIdentifier);
  List<String> findUniqueEntityRefs(String accountIdentifier);
  UpdateResult updateEntityRef(String accountIdentifier, String entityRef, String modifiedEntityRef);
}
