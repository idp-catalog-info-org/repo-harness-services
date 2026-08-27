/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.clients;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.LayoutIngestRequest;
import io.harness.spec.server.idp.v1.model.LayoutRequest;
import io.harness.spec.server.idp.v1.model.WorkflowExecutionRequest;

import javax.validation.constraints.NotEmpty;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Url;

@OwnedBy(IDP)
public interface BackstageResourceClient {
  String CATALOG_API = "{accountIdentifier}/idp/api/catalog";
  String LAYOUT_API = "{accountIdentifier}/idp/api/layout";
  String LAYOUT_API_V2 = "{accountIdentifier}/idp/api/layout/v2";
  String HARNESS_REFRESH_API = "{accountIdentifier}/idp/api/catalog/harness/provider";
  String HARNESS_TO_IDP_SYNC_API = "{accountIdentifier}/idp/api/catalog/sync/provider";
  String SCAFFOLDER_API = "{accountIdentifier}/idp/api/scaffolder";
  String SCAFFOLDER_API_V2 = "{accountIdentifier}/idp/api/scaffolder/v2";
  String CUSTOM_PROPERTIES_PATH = "/custom-properties";
  String METADATA_PATH = "/metadata";
  String HARNESS_TOKEN = "{accountIdentifier}/idp/api/auth/harnessToken";

  @GET Call<Object> getCatalogEntities(@Url String url);

  @GET(CATALOG_API + "/entities/by-name/{name}")
  Call<Object> getCatalogEntityByName(
      @Path("accountIdentifier") String accountIdentifier, @Path(value = "name", encoded = true) String name);

  @GET Call<Object> getCatalogEntityFacets(@Url String url);

  @POST(CATALOG_API + "/locations")
  Call<Object> createCatalogLocation(@Path("accountIdentifier") String accountIdentifier,

      @Body io.harness.clients.BackstageCatalogLocationCreateRequest request);

  @POST(CATALOG_API + CUSTOM_PROPERTIES_PATH)
  Call<Object> createOrUpdateCustomProperties(
      @Path("accountIdentifier") String accountIdentifier, @Body io.harness.clients.CustomPropertiesRequest request);

  @HTTP(method = "DELETE", path = CATALOG_API + CUSTOM_PROPERTIES_PATH, hasBody = true)
  Call<Object> deleteCustomProperties(@Path("accountIdentifier") String accountIdentifier,
      @Body io.harness.clients.CustomPropertiesDeleteRequest request);

  @POST(CATALOG_API + METADATA_PATH)
  Call<Object> updateCatalogMetadata(
      @Path("accountIdentifier") String accountIdentifier, @Body io.harness.clients.CatalogMetadataRequest request);

  @GET(LAYOUT_API) Call<Object> getAllLayouts(@Path("accountIdentifier") String accountIdentifier);
  @GET(LAYOUT_API_V2) Call<Object> getAllLayoutsV2(@Path("accountIdentifier") String accountIdentifier);

  @GET(LAYOUT_API + "/{layoutId}")
  Call<Object> getLayout(
      @Path("accountIdentifier") String accountIdentifier, @Path("layoutId") @NotEmpty String layoutId);

  @GET(LAYOUT_API + "/health") Call<Object> getHealth(@Path("accountIdentifier") String accountIdentifier);

  @POST(LAYOUT_API)
  Call<Object> createLayout(@Body LayoutRequest body, @Path("accountIdentifier") String accountIdentifier);

  @HTTP(method = "DELETE", path = LAYOUT_API, hasBody = true)
  Call<Object> deleteLayout(@Body LayoutRequest body, @Path("accountIdentifier") String accountIdentifier);

  @POST(LAYOUT_API + "/ingest")
  Call<Object> ingestLayout(@Body LayoutIngestRequest body, @Path("accountIdentifier") String accountIdentifier);

  @GET(HARNESS_REFRESH_API + "/refresh"
      + "/{userGroupIdentifier}")
  Call<Object>
  providerRefresh(
      @Path("accountIdentifier") String accountIdentifier, @Path("userGroupIdentifier") String userGroupIdentifier);

  @POST(HARNESS_TO_IDP_SYNC_API)
  Call<Object> harnessToIdpSync(
      @Path("accountIdentifier") String accountIdentifier, @Body io.harness.clients.HarnessToIDPSyncRequest request);

  @GET(SCAFFOLDER_API + "/tasks/list")
  Call<Object> scaffolderListTasks(
      @Path("accountIdentifier") String accountIdentifier, @Query("listFrom") long listFrom);

  @GET(SCAFFOLDER_API + "/tasks/list/paginated")
  Call<Object> scaffolderListTasksPaginated(@Path("accountIdentifier") String accountIdentifier,
      @Query("listFrom") long listFrom, @Query("listTo") long listTo, @Query("page") int page,
      @Query("limit") int limit);

  @GET(SCAFFOLDER_API_V2 + "/tasks/{taskId}")
  Call<Object> getScaffolderTask(@Path("accountIdentifier") String accountIdentifier, @Path("taskId") String taskId);

  @POST(SCAFFOLDER_API_V2 + "/tasks")
  Call<Object> executeScaffolderTask(
      @Path("accountIdentifier") String accountIdentifier, @Body BackstageScaffolderTaskRequest body);

  @GET(HARNESS_TOKEN + "/refresh")
  Call<Object> getUserSpecificToken(@Path("accountIdentifier") String accountIdentifier,
      @Query("x-harness-user-email") String userEmail, @Query("x-harness-user-uuid") String userUuid);
}
