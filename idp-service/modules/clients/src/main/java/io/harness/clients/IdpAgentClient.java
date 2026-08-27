/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.clients;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

@OwnedBy(IDP)
public interface IdpAgentClient {
  String SEARCH_TECH_DOCS_API = "/search-tech-docs";
  String ASK_GEN_AI_API = "/ask-genai";

  @POST(SEARCH_TECH_DOCS_API)
  Call<IdpAgentSearchTechDocsResponse> searchTechDocs(@Body IdpAgentSearchTechDocsRequest requestBody);

  @POST(ASK_GEN_AI_API) Call<IdpAgentAskGenAIResponse> askGenAI(@Body IdpAgentAskGenAIRequest requestBody);
}
