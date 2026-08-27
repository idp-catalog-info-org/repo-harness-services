/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.clients;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.AggregationRuleResponse;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityValidateRequest;
import io.harness.spec.server.idp.v1.model.EntityValidateResponse;
import io.harness.spec.server.idp.v1.model.ScorecardResponse;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

@OwnedBy(IDP)
public interface IdpResourceClient {
  String SCORECARDS_API = "v1/scorecards";
  String AGGREGATION_RULES_API = "v1/aggregation-rules";
  String ENTITIES_API = "/v1/entities";

  @GET(SCORECARDS_API) Call<List<ScorecardResponse>> getScorecards(@Header("Harness-Account") String accountIdentifier);

  @GET(AGGREGATION_RULES_API)
  Call<List<AggregationRuleResponse>> getAggregationRules(@Header("Harness-Account") String accountIdentifier);

  @GET(AGGREGATION_RULES_API)
  Call<List<AggregationRuleResponse>> getAggregationRules(@Header("Harness-Account") String accountIdentifier,
      @Query(value = "page") Integer page, @Query(value = "limit") Integer limit);

  @GET(ENTITIES_API)
  Call<List<EntityResponse>> getEntities(@Header("Harness-Account") String accountIdentifier,
      @Query(value = "scopes") String scopes, @Query(value = "entity_refs") String entityRefs);

  @POST(ENTITIES_API + "/validate-yaml")
  Call<List<EntityValidateResponse>> validateYaml(
      @Body EntityValidateRequest entityValidateRequest, @Header("Harness-Account") String accountIdentifier);
}
