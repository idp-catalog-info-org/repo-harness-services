/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.agent.resource;

import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.clients.IdpAgentClient;
import io.harness.clients.IdpAgentSearchTechDocsRequest;
import io.harness.clients.IdpAgentSearchTechDocsResponse;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.IdpAgentProxyApi;
import io.harness.spec.server.idp.v1.model.MatchingDoc;
import io.harness.spec.server.idp.v1.model.SearchTechDocsRequest;
import io.harness.spec.server.idp.v1.model.SearchTechDocsResponse;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Timed
@ResponseMetered
public class IdpAgentProxyApiImpl implements IdpAgentProxyApi {
  IdpAgentClient idpAgentClient;
  CatalogServiceHelper catalogServiceHelper;

  @Override
  public Response semanticSearchTechDocs(@Valid SearchTechDocsRequest body, String harnessAccount) {
    IdpAgentSearchTechDocsRequest idpAgentSearchTechDocsRequest =
        IdpAgentSearchTechDocsRequest.builder().query(body.getQuery()).accountID(harnessAccount).build();
    SearchTechDocsResponse searchTechDocsResponse = new SearchTechDocsResponse();
    try {
      IdpAgentSearchTechDocsResponse idpAgentSearchTechDocsResponse =
          getGeneralResponse(idpAgentClient.searchTechDocs(idpAgentSearchTechDocsRequest));
      if (idpAgentSearchTechDocsResponse != null && idpAgentSearchTechDocsResponse.getDocs() != null) {
        Map<String, List<IdpAgentSearchTechDocsResponse.TechDoc>> docsMap = new HashMap<>();
        for (IdpAgentSearchTechDocsResponse.TechDoc doc : idpAgentSearchTechDocsResponse.getDocs()) {
          String entityRef = doc.getKind() + ":" + doc.getScope() + "/" + doc.getEntityId();
          List<IdpAgentSearchTechDocsResponse.TechDoc> techDocsForEntity =
              docsMap.computeIfAbsent(entityRef, k -> new ArrayList<>());
          techDocsForEntity.add(doc);
        }
        Set<String> allowedEntityRefs =
            catalogServiceHelper.checkEntityRefsPermission(harnessAccount, docsMap.keySet(), "view");
        allowedEntityRefs.forEach(entityRef -> {
          List<IdpAgentSearchTechDocsResponse.TechDoc> techDocsForEntity = docsMap.get(entityRef);
          for (IdpAgentSearchTechDocsResponse.TechDoc doc : techDocsForEntity) {
            MatchingDoc matchingDoc = new MatchingDoc();
            matchingDoc.setContent(doc.getContent());
            matchingDoc.setEntityId(doc.getEntityId());
            matchingDoc.setKind(doc.getKind());
            matchingDoc.setScope(doc.getScope());
            matchingDoc.setDocPath(doc.getDocPath());
            searchTechDocsResponse.add(matchingDoc);
          }
        });
      }
      return Response.status(Response.Status.OK).entity(searchTechDocsResponse).build();
    } catch (Exception e) {
      log.error("Error calling IDP agent search API", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error processing search request").build();
    }
  }
}
