/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.graph.resources;

import io.harness.annotations.dev.*;
import io.harness.exception.EntityNotFoundException;
import io.harness.idp.catalog.graph.service.GraphTraversalService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.annotations.PublicApi;
import io.harness.spec.server.idp.v1.GraphApi;
import io.harness.spec.server.idp.v1.model.GraphTraversalResponse;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Timed
@ResponseMetered
public class GraphApiImpl implements GraphApi {
  private static final String GRAPH_TRAVERSE_FLOW_LOG = "[graphTraverse flow]";
  private GraphTraversalService graphTraversalService;

  @Override
  public Response traverseEntityGraph(
      String entityRef, String harnessAccount, List<String> relationshipType, List<String> kind, Integer depth) {
    List<String> relationshipTypes = relationshipType != null ? relationshipType : Collections.emptyList();
    List<String> kinds = CollectionUtils.isNotEmpty(kind) ? kind : Collections.emptyList();
    int traversalDepth = (depth != null) ? depth : 1;
    log.info("{} Received graph traverse request account={} entityRef={} relationshipTypes={} kinds={} depth={}",
        GRAPH_TRAVERSE_FLOW_LOG, harnessAccount, entityRef, relationshipTypes, kinds, traversalDepth);

    try {
      GraphTraversalResponse graphTraversalResponse =
          graphTraversalService.traverse(harnessAccount, entityRef, relationshipTypes, kinds, traversalDepth);
      log.info("{} Completed graph traverse request account={} entityRef={} nodes={} edges={} maxDepthReached={}",
          GRAPH_TRAVERSE_FLOW_LOG, harnessAccount, entityRef,
          graphTraversalResponse.getNodes() == null ? 0 : graphTraversalResponse.getNodes().size(),
          graphTraversalResponse.getEdges() == null ? 0 : graphTraversalResponse.getEdges().size(),
          graphTraversalResponse.getMetadata() == null ? null
                                                       : graphTraversalResponse.getMetadata().getMaxDepthReached());
      return Response.ok(graphTraversalResponse).build();
    } catch (EntityNotFoundException e) {
      log.warn("{} Graph traverse root entity not found account={} entityRef={}. Error={}", GRAPH_TRAVERSE_FLOW_LOG,
          harnessAccount, entityRef, e.getMessage());
      return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
    } catch (Exception e) {
      log.error("{} Graph traverse request failed account={} entityRef={} depth={}. Error={}", GRAPH_TRAVERSE_FLOW_LOG,
          harnessAccount, entityRef, traversalDepth, e.getMessage(), e);
      throw e;
    }
  }
}
