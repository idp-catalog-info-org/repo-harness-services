/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.clients;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface POServerClient {
  @POST("/api/environments/compile")
  Call<Object> getCompileEnvironmentYaml(@Body CompileRequestBody compileRequestBody,
      @Header("Harness-Token") String globalToken, @Query("account") String account,
      @Header("Harness-Account") String accountId);

  @POST("/api/infrastructures/execute")
  Call<Object> executeEnvironmentYaml(@Body ExecuteRequestBody executeRequestBody,
      @Header("Harness-Token") String globalToken, @Header("Harness-Account") String accountId);

  @DELETE("/api/infrastructures/{id}")
  Call<ResponseBody> deleteInfrastructure(@Header("Harness-Token") String globalToken,
      @Header("Harness-Account") String accountId, @Path("id") String infrastructureId,
      @Query("orgIdentifier") String orgIdentifier, @Query("projectIdentifier") String projectIdentifier);
}
