/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.clients.integrationmanager;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(IDP)
public interface IntegrationManagerClient {
  // List Integration Configs
  @GET("/api/v1/accounts/{accountIdentifier}/integration-configs")
  Call<List<TypesIntegrationConfig>> listIntegrationConfigs(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier,
      @Query("integration_type") List<TypesIntegrationConfig.EnumIntegrationType> integrationTypes,
      @Query("all_child_scope") boolean allChildScope, @Query("enabled") boolean enabled);

  // List Entity Mappings
  @GET("/api/v1/accounts/{accountIdentifier}/integration-configs/{integrationconfig_identifier}/mappings")
  Call<List<TypesEntityMapping>> listEntityMappings(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier, @Query("mapping_id") String mappingId);

  @GET("/api/v1/accounts/{accountIdentifier}/orgs/{orgIdentifier}/projects/{projectIdentifier}/"
      + "integration-configs/{integrationconfig_identifier}/mappings")
  Call<List<TypesEntityMapping>>
  listEntityMappingsForProject(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier, @Path("orgIdentifier") String orgIdentifier,
      @Path("projectIdentifier") String projectIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier, @Query("mapping_id") String mappingId);

  @GET("/api/v1/accounts/{accountIdentifier}/orgs/{orgIdentifier}/integration-configs/"
      + "{integrationconfig_identifier}/mappings")
  Call<List<TypesEntityMapping>>
  listEntityMappingsForOrg(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier, @Path("orgIdentifier") String orgIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier, @Query("mapping_id") String mappingId);

  // Subscribe to Entity Updates
  @POST("/api/v1/accounts/{accountIdentifier}/integration-configs/{integrationconfig_identifier}/entities/"
      + "subscribe")
  Call<EntitySubscribeEntitiesResponse>
  subscribeToEntityUpdates(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier,
      @Body OpenapiSubscribeEntitiesRequest openapiSubscribeEntitiesRequest);

  @POST("/api/v1/accounts/{accountIdentifier}/orgs/{orgIdentifier}/projects/{projectIdentifier}/"
      + "integration-configs/{integrationconfig_identifier}/entities/subscribe")
  Call<EntitySubscribeEntitiesResponse>
  subscribeToEntityUpdatesForProject(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier, @Path("orgIdentifier") String orgIdentifier,
      @Path("projectIdentifier") String projectIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier,
      @Body OpenapiSubscribeEntitiesRequest openapiSubscribeEntitiesRequest);

  @POST("/api/v1/accounts/{accountIdentifier}/orgs/{orgIdentifier}/integration-configs/"
      + "{integrationconfig_identifier}/entities/subscribe")
  Call<EntitySubscribeEntitiesResponse>
  subscribeToEntityUpdatesForOrg(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier, @Path("orgIdentifier") String orgIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier,
      @Body OpenapiSubscribeEntitiesRequest openapiSubscribeEntitiesRequest);

  // Unsubscribe from Entity Updates
  @POST("/api/v1/accounts/{accountIdentifier}/integration-configs/{integrationconfig_identifier}/entities/"
      + "unsubscribe")
  Call<EntitySubscribeEntitiesResponse>
  unsubscribeFromEntityUpdates(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier,
      @Body OpenapiSubscribeEntitiesRequest openapiSubscribeEntitiesRequest);

  @POST("/api/v1/accounts/{accountIdentifier}/orgs/{orgIdentifier}/projects/{projectIdentifier}/"
      + "integration-configs/{integrationconfig_identifier}/entities/unsubscribe")
  Call<EntitySubscribeEntitiesResponse>
  unsubscribeFromEntityUpdatesForProject(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier, @Path("orgIdentifier") String orgIdentifier,
      @Path("projectIdentifier") String projectIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier,
      @Body OpenapiSubscribeEntitiesRequest openapiSubscribeEntitiesRequest);

  @POST("/api/v1/accounts/{accountIdentifier}/orgs/{orgIdentifier}/integration-configs/"
      + "{integrationconfig_identifier}/entities/unsubscribe")
  Call<EntitySubscribeEntitiesResponse>
  unsubscribeFromEntityUpdatesForOrg(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier, @Path("orgIdentifier") String orgIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier,
      @Body OpenapiSubscribeEntitiesRequest openapiSubscribeEntitiesRequest);

  // Get Mapped Entities
  @POST("/api/v1/accounts/{accountIdentifier}/integration-configs/{integrationconfig_identifier}/"
      + "mapped-entities")
  Call<EntityMappedEntityResponseObject>
  getMappedEntities(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier, @Query("mapping_id") String mappingId,
      @Query("detailed") boolean detailed, @Query("sort_by") String sortBy, @Query("order") String order,
      @Query("page") int page, @Query("limit") int limit, @Query("searchterm") String searchTerm,
      @Query("unsubscribed_only") boolean unsubscribedOnly,
      @Body OpenapiGetMappedEntitiesRequest openapiGetMappedEntitiesRequest);

  @POST("/api/v1/accounts/{accountIdentifier}/orgs/{orgIdentifier}/projects/{projectIdentifier}/"
      + "integration-configs/{integrationconfig_identifier}/mapped-entities")
  Call<EntityMappedEntityResponseObject>
  getMappedEntitiesForProject(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier, @Path("orgIdentifier") String orgIdentifier,
      @Path("projectIdentifier") String projectIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier, @Query("mapping_id") String mappingId,
      @Query("detailed") boolean detailed, @Query("sort_by") String sortBy, @Query("order") String order,
      @Query("page") int page, @Query("limit") int limit, @Query("searchterm") String searchTerm,
      @Query("unsubscribed_only") boolean unsubscribedOnly,
      @Body OpenapiGetMappedEntitiesRequest openapiGetMappedEntitiesRequest);

  @POST("/api/v1/accounts/{accountIdentifier}/orgs/{orgIdentifier}/integration-configs/"
      + "{integrationconfig_identifier}/mapped-entities")
  Call<EntityMappedEntityResponseObject>
  getMappedEntitiesForOrg(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier, @Path("orgIdentifier") String orgIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier, @Query("mapping_id") String mappingId,
      @Query("detailed") boolean detailed, @Query("sort_by") String sortBy, @Query("order") String order,
      @Query("page") int page, @Query("limit") int limit, @Query("searchterm") String searchTerm,
      @Query("unsubscribed_only") boolean unsubscribedOnly,
      @Body OpenapiGetMappedEntitiesRequest openapiGetMappedEntitiesRequest);

  @POST("/api/v1/accounts/{accountIdentifier}/integration-configs/{integrationconfig_identifier}/"
      + "mapped-entities")
  Call<EntityMappedEntityResponseObject>
  getMappedEntitiesByOffset(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier, @Query("mapping_id") String mappingId,
      @Query("detailed") boolean detailed, @Query("sort_by") String sortBy, @Query("order") String order,
      @Query("offset") int offset, @Query("limit") int limit, @Query("searchterm") String searchTerm,
      @Query("unsubscribed_only") boolean unsubscribedOnly,
      @Body OpenapiGetMappedEntitiesRequest openapiGetMappedEntitiesRequest);

  @POST("/api/v1/accounts/{accountIdentifier}/orgs/{orgIdentifier}/projects/{projectIdentifier}/"
      + "integration-configs/{integrationconfig_identifier}/mapped-entities")
  Call<EntityMappedEntityResponseObject>
  getMappedEntitiesByOffsetForProject(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier, @Path("orgIdentifier") String orgIdentifier,
      @Path("projectIdentifier") String projectIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier, @Query("mapping_id") String mappingId,
      @Query("detailed") boolean detailed, @Query("sort_by") String sortBy, @Query("order") String order,
      @Query("offset") int offset, @Query("limit") int limit, @Query("searchterm") String searchTerm,
      @Query("unsubscribed_only") boolean unsubscribedOnly,
      @Body OpenapiGetMappedEntitiesRequest openapiGetMappedEntitiesRequest);

  @POST("/api/v1/accounts/{accountIdentifier}/orgs/{orgIdentifier}/integration-configs/"
      + "{integrationconfig_identifier}/mapped-entities")
  Call<EntityMappedEntityResponseObject>
  getMappedEntitiesByOffsetForOrg(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier, @Path("orgIdentifier") String orgIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier, @Query("mapping_id") String mappingId,
      @Query("detailed") boolean detailed, @Query("sort_by") String sortBy, @Query("order") String order,
      @Query("offset") int offset, @Query("limit") int limit, @Query("searchterm") String searchTerm,
      @Query("unsubscribed_only") boolean unsubscribedOnly,
      @Body OpenapiGetMappedEntitiesRequest openapiGetMappedEntitiesRequest);

  // Get Integration Config
  @GET("/api/v1/accounts/{accountIdentifier}/integration-configs/{integrationconfig_identifier}")
  Call<TypesIntegrationConfig> getIntegrationConfig(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier);

  @GET("/api/v1/accounts/{accountIdentifier}/orgs/{orgIdentifier}/projects/{projectIdentifier}/"
      + "integration-configs/{integrationconfig_identifier}")
  Call<TypesIntegrationConfig>
  getIntegrationConfigForProject(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier, @Path("orgIdentifier") String orgIdentifier,
      @Path("projectIdentifier") String projectIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier);

  @GET("/api/v1/accounts/{accountIdentifier}/orgs/{orgIdentifier}/integration-configs/"
      + "{integrationconfig_identifier}")
  Call<TypesIntegrationConfig>
  getIntegrationConfigForOrg(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier, @Path("orgIdentifier") String orgIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier);

  // Update Integration Config
  @PUT("/api/v1/accounts/{accountIdentifier}/integration-configs/{integrationconfig_identifier}")
  Call<TypesIntegrationConfig> updateIntegrationConfig(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier,
      @Body OpenapiUpdateIntegrationConfigRequest openapiUpdateIntegrationConfigRequest);

  @PUT("/api/v1/accounts/{accountIdentifier}/orgs/{orgIdentifier}/projects/{projectIdentifier}/"
      + "integration-configs/{integrationconfig_identifier}")
  Call<TypesIntegrationConfig>
  updateIntegrationConfigForProject(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier, @Path("orgIdentifier") String orgIdentifier,
      @Path("projectIdentifier") String projectIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier,
      @Body OpenapiUpdateIntegrationConfigRequest openapiUpdateIntegrationConfigRequest);

  @PUT("/api/v1/accounts/{accountIdentifier}/orgs/{orgIdentifier}/integration-configs/"
      + "{integrationconfig_identifier}")
  Call<TypesIntegrationConfig>
  updateIntegrationConfigForOrg(@Header("Harness-Account") String harnessAccount,
      @Path("accountIdentifier") String accountIdentifier, @Path("orgIdentifier") String orgIdentifier,
      @Path("integrationconfig_identifier") String integrationConfigIdentifier,
      @Body OpenapiUpdateIntegrationConfigRequest openapiUpdateIntegrationConfigRequest);
}
