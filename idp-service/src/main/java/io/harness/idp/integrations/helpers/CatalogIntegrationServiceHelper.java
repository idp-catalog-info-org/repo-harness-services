/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.helpers;

import io.harness.annotations.dev.*;
import io.harness.clients.integrationmanager.IntegrationManagerClientHelper;
import io.harness.clients.integrationmanager.OpenapiSubscribeEntitiesRequest;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class CatalogIntegrationServiceHelper {
  @Inject IntegrationManagerClientHelper integrationManagerClientHelper;

  public String[] parseSpacePath(String spacePath) {
    String[] parts = spacePath.split("\\.");
    String orgIdentifier = parts.length >= 2 ? parts[1] : null;
    String projectIdentifier = parts.length >= 3 ? parts[2] : null;
    return new String[] {orgIdentifier, projectIdentifier};
  }

  @SuppressWarnings("unchecked")
  public Map<String, String> collectEntityUuidToKind(Map<String, Object> kindsMap) {
    Map<String, String> uuidToKind = new HashMap<>();
    for (Map.Entry<String, Object> kindEntry : kindsMap.entrySet()) {
      if (!(kindEntry.getValue() instanceof Map)) {
        continue;
      }
      String kind = kindEntry.getKey();
      Map<String, Object> kindMap = (Map<String, Object>) kindEntry.getValue();
      Object directUuid = kindMap.get("entity_uuid");
      if (directUuid instanceof String) {
        uuidToKind.put((String) directUuid, kind);
        continue;
      }
      for (Map.Entry<String, Object> subEntry : kindMap.entrySet()) {
        if (subEntry.getValue() instanceof Map) {
          Object nestedUuid = ((Map<String, Object>) subEntry.getValue()).get("entity_uuid");
          if (nestedUuid instanceof String) {
            uuidToKind.put((String) nestedUuid, kind);
          }
        }
      }
    }
    return uuidToKind;
  }

  public OpenapiSubscribeEntitiesRequest prepareSubscribeEntitiesRequest(String kind, String uuid) {
    OpenapiSubscribeEntitiesRequest subscribeEntitiesRequest = new OpenapiSubscribeEntitiesRequest();
    subscribeEntitiesRequest.setEntities(
        List.of(OpenapiSubscribeEntitiesRequest.EntityEntityReference.builder()
                    .mappingId(integrationManagerClientHelper.getIntegrationManagerIdpMappingId())
                    .kind(kind)
                    .uuid(uuid)
                    .build()));
    return subscribeEntitiesRequest;
  }
}
